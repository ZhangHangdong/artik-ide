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
import org.eclipse.che.ide.api.action.ActionEvent;
import org.eclipse.che.ide.api.action.ActionManager;
import org.eclipse.che.ide.api.action.DefaultActionGroup;
import org.eclipse.che.ide.extension.machine.client.processes.ProcessTreeNode;
import org.eclipse.che.ide.extension.machine.client.processes.panel.ProcessesPanelPresenter;
import org.eclipse.che.plugin.artik.ide.ArtikLocalizationConstant;

/**
 * Switch Artik profile menu group.
 *
 * @author Vitaliy Guliy
 */
@Singleton
public class ArtikProfileContextMenuGroup extends DefaultActionGroup {

    private final ProcessesPanelPresenter processesPanelPresenter;

    @Inject
    public ArtikProfileContextMenuGroup(ArtikLocalizationConstant locale,
                                        ProcessesPanelPresenter processesPanelPresenter,
                                        ActionManager actionManager) {
        super(locale.artikProfileActionTitle(), true, actionManager);
        this.processesPanelPresenter = processesPanelPresenter;
    }

    @Override
    public void update(ActionEvent event) {
        ProcessTreeNode processTreeNode = processesPanelPresenter.getContextTreeNode();

        if (processTreeNode == null) {
            event.getPresentation().setEnabled(false);
            event.getPresentation().setVisible(false);
            return;
        }

        if (ProcessTreeNode.ProcessNodeType.MACHINE_NODE != processTreeNode.getType() ||
                !(processTreeNode.getData() instanceof Machine)) {
            event.getPresentation().setEnabled(false);
            event.getPresentation().setVisible(false);
            return;
        }

        Machine machine = (Machine)processTreeNode.getData();

        String type = machine.getConfig().getType();
        if (!"ssh".equals(type) && !"artik".equals(type)) {
            event.getPresentation().setEnabled(false);
            event.getPresentation().setVisible(false);
            return;
        }

        event.getPresentation().setEnabled(true);
        event.getPresentation().setVisible(true);
    }

    @Override
    public void actionPerformed(ActionEvent event) {
    }

}
