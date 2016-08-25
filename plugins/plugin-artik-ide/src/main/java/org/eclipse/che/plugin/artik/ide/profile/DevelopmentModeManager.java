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

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;

import org.eclipse.che.api.core.model.machine.Command;
import org.eclipse.che.api.core.model.machine.Machine;
import org.eclipse.che.api.machine.shared.dto.CommandDto;
import org.eclipse.che.api.machine.shared.dto.MachineDto;
import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.OperationException;
import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.api.promises.client.PromiseError;
import org.eclipse.che.api.promises.client.callback.AsyncPromiseHelper;
import org.eclipse.che.ide.api.action.ActionManager;
import org.eclipse.che.ide.api.action.DefaultActionGroup;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.dialogs.ConfirmCallback;
import org.eclipse.che.ide.api.dialogs.DialogFactory;
import org.eclipse.che.ide.api.machine.MachineServiceClient;
import org.eclipse.che.ide.api.notification.NotificationManager;
import org.eclipse.che.ide.api.notification.StatusNotification;
import org.eclipse.che.ide.commons.exception.UnmarshallerException;
import org.eclipse.che.ide.dto.DtoFactory;
import org.eclipse.che.ide.extension.machine.client.machine.MachineStateEvent;
import org.eclipse.che.ide.extension.machine.client.processes.panel.ProcessesPanelPresenter;
import org.eclipse.che.ide.util.UUID;
import org.eclipse.che.ide.util.loging.Log;
import org.eclipse.che.ide.websocket.Message;
import org.eclipse.che.ide.websocket.MessageBus;
import org.eclipse.che.ide.websocket.MessageBusProvider;
import org.eclipse.che.ide.websocket.WebSocketException;
import org.eclipse.che.ide.websocket.rest.SubscriptionHandler;
import org.eclipse.che.ide.websocket.rest.Unmarshallable;
import org.eclipse.che.plugin.artik.ide.ArtikExtension;
import org.eclipse.che.plugin.artik.ide.ArtikLocalizationConstant;
import org.eclipse.che.plugin.artik.ide.ArtikResources;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.eclipse.che.api.core.model.machine.MachineStatus.RUNNING;
import static org.eclipse.che.api.promises.client.callback.AsyncPromiseHelper.createFromAsyncRequest;

/**
 * Manager for switching profile at Artik devices.
 *
 * @author Vitaliy Guliy
 */
@Singleton
public class DevelopmentModeManager implements MachineStateEvent.Handler {

    private final ActionManager             actionManager;
    private final MachineServiceClient      machineService;
    private final AppContext                appContext;
    private final ArtikModeActionFactory    artikModeActionFactory;
    private final MessageBus                messageBus;
    private final DtoFactory                dtoFactory;
    private final ArtikResources            resources;
    private final ProcessesPanelPresenter   processesPanelPresenter;
    private final DialogFactory             dialogFactory;
    private final NotificationManager       notificationManager;
    private final ArtikLocalizationConstant locale;

    /**
     * A set of ssh machines.
     * Use machine name for a key and machine ID for a value.
     */
    private final Map<String, Machine>              sshMachines;

    private final Map<String, DefaultActionGroup>   menus;

    private       AsyncCallback<String>             commandCallback;

    private StatusNotification progressNotification;

    @Inject
    public DevelopmentModeManager(ActionManager actionManager,
                                  MachineServiceClient machineService,
                                  AppContext appContext,
                                  ArtikModeActionFactory artikModeActionFactory,
                                  EventBus eventBus,
                                  MessageBusProvider messageBusProvider,
                                  DtoFactory dtoFactory,
                                  ArtikResources resources,
                                  ProcessesPanelPresenter processesPanelPresenter,
                                  DialogFactory dialogFactory,
                                  NotificationManager notificationManager,
                                  ArtikLocalizationConstant locale) {
        this.actionManager = actionManager;
        this.machineService = machineService;
        this.appContext = appContext;
        this.artikModeActionFactory = artikModeActionFactory;
        this.messageBus = messageBusProvider.getMessageBus();
        this.dtoFactory = dtoFactory;
        this.resources = resources;
        this.processesPanelPresenter = processesPanelPresenter;
        this.dialogFactory = dialogFactory;
        this.notificationManager = notificationManager;
        this.locale = locale;

        sshMachines = new HashMap<>();
        menus = new HashMap<>();

        eventBus.addHandler(MachineStateEvent.TYPE, this);
    }

    private native void log(String msg) /*-{
        console.log(msg);
    }-*/;

    public void turnOnDevelopmentMode(final String machineName) {
        dialogFactory.createConfirmDialog("IDE", "Development mode will install software and dependencies for Artik IDE.<br>" +
                        "Are you sure you want to turn on development mode for <b>" + machineName + "</b>?",
                        "Yes", "Cancel",
                new ConfirmCallback() {
                    @Override
                    public void accepted() {
                        String notificationMessage = "Turning on development mode for " + machineName;
                        progressNotification = notificationManager.notify(notificationMessage, StatusNotification.Status.PROGRESS,
                                StatusNotification.DisplayMode.FLOAT_MODE);

                        Machine machine = sshMachines.get(machineName);
                        String cmd = resources.turnOnDevelopmentProfileCommand().getText();
                        executeCommand(cmd, machine.getId()).then(new Operation<String>() {
                            @Override
                            public void apply(String arg) throws OperationException {
                                String message = "Development mode has been turned on for " + machineName;
                                progressNotification.setTitle(message);
                                progressNotification.setStatus(StatusNotification.Status.SUCCESS);
                            }
                        });
                    }
                }, null).show();
    }

    public void turnOnProductionMode(final String machineName) {
        dialogFactory.createConfirmDialog("IDE",
                        "Production mode will uninstall software and dependencies for Artik IDE, and delete projects backups.<br>" +
                        "It cannot be undone!<br>" +
                        "Are you sure you want to turn on production mode for <b>" + machineName + "</b>?",
                        "Yes", "Cancel",
                new ConfirmCallback() {
                    @Override
                    public void accepted() {
                        String notificationMessage = "Turning on production mode for " + machineName;
                        progressNotification = notificationManager.notify(notificationMessage, StatusNotification.Status.PROGRESS,
                                StatusNotification.DisplayMode.FLOAT_MODE);

                        Machine machine = sshMachines.get(machineName);
                        String cmd = resources.turnOnProductionProfileCommand().getText();
                        executeCommand(cmd, machine.getId()).then(new Operation<String>() {
                            @Override
                            public void apply(String arg) throws OperationException {
                                String message = "Production mode has been turned on for " + machineName;
                                progressNotification.setTitle(message);
                                progressNotification.setStatus(StatusNotification.Status.SUCCESS);
                            }
                        });
                    }
                }, null).show();
    }

    /**
     * Fetches all ssh machines from workspace converts them to actions and adds to push to device action group.
     */
    public void fetchSshMachines() {
        final DefaultActionGroup artikGroup =  (DefaultActionGroup)actionManager.getAction(ArtikExtension.ARTIK_GROUP_MAIN_MENU_ID);
        artikGroup.addSeparator();

        Promise<List<MachineDto>> machines = machineService.getMachines(appContext.getWorkspaceId());
        machines.then(new Operation<List<MachineDto>>() {
            @Override
            public void apply(List<MachineDto> machines) throws OperationException {
                for (Machine machine : machines) {
                    String type = machine.getConfig().getType();
                    if (!"ssh".equals(type) && !"artik".equals(type)) {
                        continue;
                    }

                    if (!RUNNING.equals(machine.getStatus()) ) {
                        continue;
                    }

                    addArtikProfileMenu(machine);
                }
            }
        }).catchError(new Operation<PromiseError>() {
            @Override
            public void apply(PromiseError error) throws OperationException {
                Log.error(getClass(), error.getMessage());
            }
        });
    }

    private void addArtikProfileMenu(Machine machine) {
        sshMachines.put(machine.getConfig().getName(), machine);

        final DefaultActionGroup profileGroup = (DefaultActionGroup)actionManager.getAction("artikProfileGroup");

        final DefaultActionGroup machineMenu = new DefaultActionGroup(machine.getConfig().getName(), true, actionManager);
        menus.put(machine.getConfig().getName(), machineMenu);

        profileGroup.add(machineMenu);

        TurnDevelopmentModeAction turnDevelopmentModeAction = artikModeActionFactory.turnDevelopmentModeAction(machine.getConfig().getName());
        machineMenu.add(turnDevelopmentModeAction);

        TurnProductionModeAction turnProductionModeAction = artikModeActionFactory.turnProductionModeAction(machine.getConfig().getName());
        machineMenu.add(turnProductionModeAction);
    }

    private void removeArtikProfileMenu(String machineName) {
        sshMachines.remove(machineName);

        final DefaultActionGroup profileGroup = (DefaultActionGroup)actionManager.getAction("artikProfileGroup");

        DefaultActionGroup machineMenu = menus.remove(machineName);
        if (machineMenu == null) {
            return;
        }

        profileGroup.remove(machineMenu);
    }

    @Override
    public void onMachineCreating(MachineStateEvent event) {
    }

    @Override
    public void onMachineRunning(MachineStateEvent event) {
        addArtikProfileMenu(event.getMachine());
    }

    @Override
    public void onMachineDestroyed(MachineStateEvent event) {
        removeArtikProfileMenu(event.getMachine().getConfig().getName());
    }

    /**
     * Executes a command and returns first message from output as a result.
     */
    private Promise<String> executeCommand(final String cmd, final String machineId) {
        final String chanel = "process:output:" + UUID.uuid();

        try {
            messageBus.subscribe(chanel, new SubscriptionHandler<String>(new CommandOutputUnmarshaller()) {
                @Override
                protected void onMessageReceived(String message) {
                    if ("[STDOUT] >>> end <<<".equals(message)) {
                        messageBus.unsubscribeSilently(chanel, this);
                        processesPanelPresenter.printMachineOutput(machineId, "");
                        commandCallback.onSuccess(message);
                    } else {
                        if (message.startsWith("[STDOUT] ")) {
                            processesPanelPresenter.printMachineOutput(machineId, message.substring(9));
                        } else if (message.startsWith("[STDERR] ")) {
                            processesPanelPresenter.printMachineOutput(machineId, message.substring(9), "red");
                        } else {
                            processesPanelPresenter.printMachineOutput(machineId, message);
                        }
                    }
                }

                @Override
                protected void onErrorReceived(Throwable throwable) {
                    messageBus.unsubscribeSilently(chanel, this);
                }
            });
        } catch (WebSocketException e) {
            commandCallback.onFailure(new Exception(e));
        }

        final Promise<String> promise = createFromAsyncRequest(new AsyncPromiseHelper.RequestCall<String>() {
            @Override
            public void makeCall(AsyncCallback<String> callback) {
                commandCallback = callback;
            }
        });

        final Command command = dtoFactory.createDto(CommandDto.class)
                .withName("name")
                .withType("custom")
                .withCommandLine(cmd);

        machineService.executeCommand(appContext.getWorkspaceId(), machineId, command, chanel);

        return promise;
    }

    private class CommandOutputUnmarshaller implements Unmarshallable<String> {

        private String payload;

        @Override
        public void unmarshal(Message response) throws UnmarshallerException {
            payload = response.getBody();
        }

        @Override
        public String getPayload() {
            return payload;
        }

    }

}
