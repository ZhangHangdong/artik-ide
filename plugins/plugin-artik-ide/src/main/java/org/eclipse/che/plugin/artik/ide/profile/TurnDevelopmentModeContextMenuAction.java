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
package org.eclipse.che.plugin.artik.ide.profile;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.che.api.core.model.machine.Machine;
import org.eclipse.che.ide.api.action.AbstractPerspectiveAction;
import org.eclipse.che.ide.api.action.ActionEvent;
import org.eclipse.che.ide.extension.machine.client.processes.ProcessTreeNode;
import org.eclipse.che.ide.extension.machine.client.processes.panel.ProcessesPanelPresenter;
import org.eclipse.che.plugin.artik.ide.ArtikLocalizationConstant;

import javax.validation.constraints.NotNull;

import static java.util.Collections.singletonList;
import static org.eclipse.che.ide.workspace.perspectives.project.ProjectPerspective.PROJECT_PERSPECTIVE_ID;

/**
 * Context menu action to turn on development mode at Artik device.
 *
 * @author Vitaliy Guliy
 */
@Singleton
public class TurnDevelopmentModeContextMenuAction extends AbstractPerspectiveAction {

    private final DevelopmentModeManager  developmentModeManager;
    private final ProcessesPanelPresenter processesPanelPresenter;

    @Inject
    public TurnDevelopmentModeContextMenuAction(ArtikLocalizationConstant locale,
                                                DevelopmentModeManager developmentModeManager,
                                                ProcessesPanelPresenter processesPanelPresenter) {
        super(singletonList(PROJECT_PERSPECTIVE_ID),
                locale.turnDevelopmentModeActionTitle(),
                locale.turnDevelopmentModeActionDescription(),
                null,
                null);

        this.developmentModeManager = developmentModeManager;
        this.processesPanelPresenter = processesPanelPresenter;
    }

    @Override
    public void updateInPerspective(@NotNull ActionEvent event) {
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        ProcessTreeNode processTreeNode = processesPanelPresenter.getContextTreeNode();
        Machine machine = (Machine)processTreeNode.getData();
        developmentModeManager.turnOnDevelopmentMode(machine.getConfig().getName());
    }

}
