/*
 * Copyright (c) 2012-2021 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.multiuser.organization.api.resource;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.Page;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.rest.Service;
import org.eclipse.che.multiuser.organization.api.DtoConverter;
import org.eclipse.che.multiuser.organization.shared.dto.OrganizationDistributedResourcesDto;
import org.eclipse.che.multiuser.organization.shared.model.OrganizationDistributedResources;
import org.eclipse.che.multiuser.resource.api.free.ResourceValidator;
import org.eclipse.che.multiuser.resource.shared.dto.ResourceDto;

/**
 * REST API for resources distribution between suborganizations.
 *
 * @author Sergii Leschenko
 */
@Tag(name = "organization-resource",
    description = "REST API for resources distribution between suborganizations")
@Path("/organization/resource")
public class OrganizationResourcesDistributionService extends Service {
  private final OrganizationResourcesDistributor resourcesDistributor;
  private final ResourceValidator resourceValidator;

  @Inject
  public OrganizationResourcesDistributionService(
      OrganizationResourcesDistributor resourcesDistributor, ResourceValidator resourceValidator) {
    this.resourcesDistributor = resourcesDistributor;
    this.resourceValidator = resourceValidator;
  }

  @POST
  @Path("/{suborganizationId}/cap")
  @Consumes(APPLICATION_JSON)
  @ApiOperation(
      value = "Cap usage of shared resources.",
      notes =
          "By default suborganization is able to use all parent organization resources."
              + "Cap allow to limit usage of shared resources by suborganization.")
  @ApiResponses({
    @ApiResponse(code = 204, message = "Resources successfully capped"),
    @ApiResponse(code = 400, message = "Missed required parameters, parameters are not valid"),
    @ApiResponse(code = 404, message = "Specified organization was not found"),
    @ApiResponse(code = 409, message = "Specified organization is root organization"),
    @ApiResponse(code = 409, message = "Suborganization is using shared resources"),
    @ApiResponse(code = 500, message = "Internal server error occurred")
  })
  public void capResources(
      @Parameter(description ="Suborganization id") @PathParam("suborganizationId") String suborganizationId,
      @Parameter(description ="Resources to cap") List<ResourceDto> resourcesCap)
      throws BadRequestException, NotFoundException, ConflictException, ServerException {
    checkArgument(resourcesCap != null, "Missed resources caps.");
    Set<String> resourcesToSet = new HashSet<>();
    for (ResourceDto resource : resourcesCap) {
      if (!resourcesToSet.add(resource.getType())) {
        throw new BadRequestException(
            format(
                "Resources to cap must contain only one resource with type '%s'.",
                resource.getType()));
      }
      resourceValidator.validate(resource);
    }

    resourcesDistributor.capResources(suborganizationId, resourcesCap);
  }

  @GET
  @Path("/{suborganizationId}/cap")
  @Produces(APPLICATION_JSON)
  @ApiOperation(
      value = "Get resources cap of specified suborganization.",
      response = OrganizationDistributedResourcesDto.class,
      responseContainer = "list")
  @ApiResponses({
    @ApiResponse(code = 200, message = "Resources caps successfully fetched"),
    @ApiResponse(code = 404, message = "Specified organization was not found"),
    @ApiResponse(code = 409, message = "Specified organization is root organization"),
    @ApiResponse(code = 500, message = "Internal server error occurred")
  })
  public List<ResourceDto> getResourcesCap(
      @Parameter(description ="Suborganization id") @PathParam("suborganizationId") String suborganization)
      throws NotFoundException, ConflictException, ServerException {
    return resourcesDistributor
        .getResourcesCaps(suborganization)
        .stream()
        .map(org.eclipse.che.multiuser.resource.api.DtoConverter::asDto)
        .collect(toList());
  }

  @GET
  @Path("/{organizationId}")
  @Produces(APPLICATION_JSON)
  @ApiOperation(
      value = "Get resources which are distributed by specified parent.",
      response = OrganizationDistributedResourcesDto.class,
      responseContainer = "list")
  @ApiResponses({
    @ApiResponse(code = 200, message = "Resources caps successfully fetched"),
    @ApiResponse(code = 500, message = "Internal server error occurred")
  })
  public Response getDistributedResources(
      @Parameter(description ="Organization id") @PathParam("organizationId") String organizationId,
      @Parameter(description = "Max items") @QueryParam("maxItems") @DefaultValue("30") int maxItems,
      @Parameter(description = "Skip count") @QueryParam("skipCount") @DefaultValue("0") long skipCount)
      throws BadRequestException, ServerException {
    checkArgument(maxItems >= 0, "The number of items to return can't be negative.");
    checkArgument(skipCount >= 0, "The number of items to skip can't be negative.");

    final Page<? extends OrganizationDistributedResources> distributedResourcesPage =
        resourcesDistributor.getByParent(organizationId, maxItems, skipCount);
    return Response.ok()
        .entity(distributedResourcesPage.getItems(DtoConverter::asDto))
        .header("Link", createLinkHeader(distributedResourcesPage))
        .build();
  }

  /**
   * Ensures the truth of an expression involving one or more parameters to the calling method.
   *
   * @param expression a boolean expression
   * @param errorMessage the exception message to use if the check fails
   * @throws BadRequestException if {@code expression} is false
   */
  private void checkArgument(boolean expression, String errorMessage) throws BadRequestException {
    if (!expression) {
      throw new BadRequestException(errorMessage);
    }
  }
}
