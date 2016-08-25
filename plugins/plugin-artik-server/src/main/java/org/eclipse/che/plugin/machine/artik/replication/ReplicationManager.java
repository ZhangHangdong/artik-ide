/*******************************************************************************
 * Copyright (c) 2016 Samsung Electronics Co., Ltd.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - Initial implementation
 *   Samsung Electronics Co., Ltd. - Initial implementation
 *******************************************************************************/
package org.eclipse.che.plugin.machine.artik.replication;

import com.google.common.annotations.Beta;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.environment.server.MachineService;
import org.eclipse.che.plugin.machine.artik.replication.shell.JsonValueHelperFactory;
import org.eclipse.che.plugin.machine.artik.replication.shell.ShellCommandManager;

import java.io.IOException;

import static java.lang.Boolean.TRUE;

/**
 * @author Dmitry Kuleshov
 *
 * @since 4.5
 */
@Singleton
@Beta
class ReplicationManager {
    static final boolean USE_REPLICATION_ROOT     = TRUE;
    static final String  DEFAULT_PROJECT_LOCATION = "/projects";

    private final ShellCommandManager    shellCommandManager;
    private final JsonValueHelperFactory jsonValueHelperFactory;

    @Inject
    public ReplicationManager(ShellCommandManager shellCommandManager, JsonValueHelperFactory jsonValueHelperFactory) {
        this.shellCommandManager = shellCommandManager;
        this.jsonValueHelperFactory = jsonValueHelperFactory;
    }

    /**
     * Runs SCP command with defined parameters.
     *
     * @param machineId identifier of a machine the SCP call is addressed to,
     *                  to get all needed information we perform a request to
     *                  {@link MachineService}
     *
     * @param sourcePath absolute source path of a folder to be copied from,
     *                   in other words location of a folder on a local machine
     *
     * @param targetPath absolute target path of a folder to be copied to,
     *                   in other words location of a folder on a remote machine
     *
     * @param useSyncFolder defines if we should use synchronization folder parameter,
     *                      if it is set to <code>true</code> than <strong>targetPath</strong>
     *                      is treated as the relative path starting from the location
     *                      pointed by syncFolder field defined in a machine's script
     *
     * @throws ServerException something gone wrong when we executed a shell command
     * @throws IOException something gone wrong when we executed a shell command
     */
    void copy(String machineId, String sourcePath, String targetPath, boolean useSyncFolder)
            throws ServerException, IOException {
        shellCommandManager.scp(jsonValueHelperFactory.create(machineId),
                                DEFAULT_PROJECT_LOCATION.concat(sourcePath),
                                targetPath,
                                useSyncFolder);

    }

    /**
     * Runs SCP command with defined parameters. The call of this method is equivalent
     * to calling {@link ReplicationManager#copy(String, String, String, boolean)}
     * with <code>useSyncFolder</code> parameter set to <code>false</code>
     */
    void copy(String machineId, String sourcePath, String targetPath)
            throws ServerException, IOException {
        copy(machineId, sourcePath, targetPath, false);
    }
}
