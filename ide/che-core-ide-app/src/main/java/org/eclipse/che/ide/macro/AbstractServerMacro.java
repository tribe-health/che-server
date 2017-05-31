/*******************************************************************************
 * Copyright (c) 2012-2017 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.ide.macro;

import com.google.common.annotations.Beta;
import com.google.web.bindery.event.shared.EventBus;

import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.machine.events.WsAgentStateEvent;
import org.eclipse.che.ide.api.machine.events.WsAgentStateHandler;
import org.eclipse.che.ide.api.macro.Macro;
import org.eclipse.che.ide.api.macro.MacroRegistry;
import org.eclipse.che.ide.api.workspace.model.MachineImpl;
import org.eclipse.che.ide.api.workspace.model.WorkspaceImpl;

import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Base macro which belongs to the current server configuration. Provides easy access to the developer machine
 * to allow fetch necessary information to use in custom commands, preview urls, etc.
 *
 * @author Vlad Zhukovskyi
 * @see MacroRegistry
 * @see Macro
 * @see ServerHostNameMacro
 * @see ServerPortMacro
 * @since 4.7.0
 */
@Beta
public abstract class AbstractServerMacro implements WsAgentStateHandler {

    private final MacroRegistry macroRegistry;
    private final AppContext    appContext;

    public AbstractServerMacro(MacroRegistry macroRegistry,
                               EventBus eventBus,
                               AppContext appContext) {
        this.macroRegistry = macroRegistry;
        this.appContext = appContext;

        eventBus.addHandler(WsAgentStateEvent.TYPE, this);
    }

    /**
     * Register macro providers which returns the implementation.
     *
     * @see AbstractServerMacro#getMacros(MachineImpl)
     * @since 4.7.0
     */
    private void registerMacros() {
        final WorkspaceImpl workspace = appContext.getWorkspace();
        final Optional<MachineImpl> devMachine = workspace.getDevMachine();

        if (!devMachine.isPresent()) {
            return;
        }

        final Set<Macro> macros = getMacros(devMachine.get());
        checkNotNull(macros);

        if (macros.isEmpty()) {
            return;
        }

        macroRegistry.register(macros);
    }

    /**
     * Unregister macro providers which the implementation returns.
     *
     * @see AbstractServerMacro#getMacros(MachineImpl)
     * @since 4.7.0
     */
    private void unregisterMacros() {
        final WorkspaceImpl workspace = appContext.getWorkspace();
        final Optional<MachineImpl> devMachine = workspace.getDevMachine();

        if (!devMachine.isPresent()) {
            return;
        }

        for (Macro provider : getMacros(devMachine.get())) {
            macroRegistry.unregister(provider);
        }
    }

    /**
     * Returns the macros which implementation provides based on the information from the developer machine.
     *
     * @param devMachine
     *         current developer machine
     * @return set of unique macro providers
     * @see Macro
     * @since 4.7.0
     */
    public abstract Set<Macro> getMacros(MachineImpl devMachine);

    /** {@inheritDoc} */
    @Override
    public void onWsAgentStarted(WsAgentStateEvent event) {
        registerMacros();
    }

    /** {@inheritDoc} */
    @Override
    public void onWsAgentStopped(WsAgentStateEvent event) {
        unregisterMacros();
    }
}
