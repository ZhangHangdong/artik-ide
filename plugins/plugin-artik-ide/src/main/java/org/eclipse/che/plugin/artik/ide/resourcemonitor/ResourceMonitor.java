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
package org.eclipse.che.plugin.artik.ide.resourcemonitor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;

import org.eclipse.che.api.core.model.machine.Command;
import org.eclipse.che.api.core.model.machine.Machine;
import org.eclipse.che.api.machine.shared.dto.CommandDto;
import org.eclipse.che.api.machine.shared.dto.MachineDto;
import org.eclipse.che.api.machine.shared.dto.MachineProcessDto;
import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.OperationException;
import org.eclipse.che.api.promises.client.PromiseError;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.machine.MachineServiceClient;
import org.eclipse.che.ide.api.workspace.event.WorkspaceStartedEvent;
import org.eclipse.che.ide.api.workspace.event.WorkspaceStoppedEvent;
import org.eclipse.che.ide.commons.exception.UnmarshallerException;
import org.eclipse.che.ide.dto.DtoFactory;
import org.eclipse.che.ide.extension.machine.client.machine.MachineStateEvent;
import org.eclipse.che.ide.extension.machine.client.processes.monitoring.MachineMonitors;
import org.eclipse.che.ide.util.UUID;
import org.eclipse.che.ide.websocket.Message;
import org.eclipse.che.ide.websocket.MessageBus;
import org.eclipse.che.ide.websocket.MessageBusProvider;
import org.eclipse.che.ide.websocket.WebSocketException;
import org.eclipse.che.ide.websocket.rest.SubscriptionHandler;
import org.eclipse.che.ide.websocket.rest.Unmarshallable;
import org.eclipse.che.plugin.artik.ide.ArtikResources;

import java.util.HashMap;
import java.util.List;

/**
 * Resources monitor asks Artik machines for CPU, memory and disk usages
 *  and displays them in the consoles tree.
 *
 * @author Vitaliy Guliy
 */
@Singleton
public class ResourceMonitor implements MachineStateEvent.Handler,
        WorkspaceStartedEvent.Handler, WorkspaceStoppedEvent.Handler {

    private final MachineServiceClient              machineServiceClient;
    private final AppContext                        appContext;
    private final MachineMonitors                   machineMonitors;
    private final ArtikResources                    resources;
    private final MessageBus                        messageBus;
    private final DtoFactory                        dtoFactory;

    private final HashMap<String, MonitorAgent>     monitorAgents;

    @Inject
    public ResourceMonitor(MachineServiceClient machineServiceClient,
                           EventBus eventBus,
                           AppContext appContext,
                           MachineMonitors machineMonitors,
                           ArtikResources resources,
                           MessageBusProvider messageBusProvider,
                           DtoFactory dtoFactory) {
        this.machineServiceClient = machineServiceClient;
        this.appContext = appContext;
        this.machineMonitors = machineMonitors;
        this.resources = resources;
        this.messageBus = messageBusProvider.getMessageBus();
        this.dtoFactory = dtoFactory;

        monitorAgents = new HashMap<>();

        eventBus.addHandler(MachineStateEvent.TYPE, this);
        eventBus.addHandler(WorkspaceStartedEvent.TYPE, this);
        eventBus.addHandler(WorkspaceStoppedEvent.TYPE, this);
    }

    @Override
    public void onMachineCreating(MachineStateEvent event) {
    }

    @Override
    public void onMachineRunning(MachineStateEvent event) {
        if ("artik".equals(event.getMachine().getConfig().getType())) {
            monitorAgents.put(event.getMachine().getId(), new MonitorAgent(event.getMachine()));
        }
    }

    @Override
    public void onMachineDestroyed(MachineStateEvent event) {
        MonitorAgent monitorAgent = monitorAgents.remove(event.getMachineId());
        if (monitorAgent != null) {
            monitorAgent.stop();
        }
    }

    @Override
    public void onWorkspaceStarted(WorkspaceStartedEvent event) {
        machineServiceClient.getMachines(appContext.getWorkspaceId()).then(new Operation<List<MachineDto>>() {
            @Override
            public void apply(List<MachineDto> machines) throws OperationException {
                for (MachineDto machine : machines) {
                    if ("artik".equals(machine.getConfig().getType())) {
                        monitorAgents.put(machine.getId(), new MonitorAgent(machine));
                    }
                }
            }
        });
    }

    @Override
    public void onWorkspaceStopped(WorkspaceStoppedEvent event) {
        for (MonitorAgent monitorAgent : monitorAgents.values()) {
            monitorAgent.stop();
        }

        monitorAgents.clear();
    }

    /**
     * Monitor agent running a special command to get the statistic,
     * listening the command output and updating the monitor widgets.
     */
    private class MonitorAgent extends SubscriptionHandler<String> {

        private final Machine           machine;

        private MachineProcessDto       machineProcessDto;
        private String                  channel;

        public MonitorAgent(Machine machine) {
            super(new CommandOutputUnmarshaller());
            this.machine = machine;

            checkMonitorProcess();
        }

        private void checkMonitorProcess() {
            machineServiceClient.getProcesses(appContext.getWorkspaceId(), machine.getId()).then(new Operation<List<MachineProcessDto>>() {
                @Override
                public void apply(List<MachineProcessDto> arg) throws OperationException {
                    for (MachineProcessDto processDto : arg) {
                        if (processDto.getCommandLine() != null && !processDto.getCommandLine().isEmpty()
                                && processDto.getCommandLine().startsWith("#hidden monitor process")) {
                            connectToProcess(processDto);
                            return;
                        }
                    }

                    runMonitorProcess();
                }
            }).catchError(new Operation<PromiseError>() {
                @Override
                public void apply(PromiseError arg) throws OperationException {
                    runMonitorProcess();
                }
            });
        }

        private void runMonitorProcess() {
            channel = "process:output:" + UUID.uuid();

            try {
                messageBus.subscribe(channel, this);

                Command command = dtoFactory.createDto(CommandDto.class)
                        .withName("name")
                        .withType("custom")
                        .withCommandLine(resources.getMonitorAllCommand().getText());

                machineServiceClient.executeCommand(appContext.getWorkspaceId(), machine.getId(), command, channel).then(new Operation<MachineProcessDto>() {
                    @Override
                    public void apply(MachineProcessDto processDto) throws OperationException {
                        machineProcessDto = processDto;
                    }
                });
            } catch (WebSocketException e) {
                // Ignore and do nothing
            }
        }

        private void connectToProcess(MachineProcessDto processDto) {
            machineProcessDto = processDto;
            channel = processDto.getOutputChannel();

            try {
                messageBus.subscribe(channel, this);
            } catch (WebSocketException e) {
                // Ignore and do nothing
            }
        }

        public void stop() {
            if (channel != null) {
                messageBus.unsubscribeSilently(channel, this);
            }

            if (machineProcessDto != null) {
                machineServiceClient.stopProcess(appContext.getWorkspaceId(), machine.getId(), machineProcessDto.getPid());
            }
        }

        @Override
        protected void onMessageReceived(String message) {
            if (message.startsWith("[STDOUT] ")) {
                message = message.substring(9);
            }

            // CPU_USED MEM_USED MEM_TOTAL DISK_USED DISK_TOTAL
            String []parts = message.split(" ");

            // CPU usage
            int cpu = (int) Math.rint(Double.valueOf(parts[0]) * 100);
            machineMonitors.setCpuUsage(machine.getId(), cpu);

            // Memory usage
            machineMonitors.setMemoryUsage(machine.getId(), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));

            // Disk usage
            machineMonitors.setDiskUsage(machine.getId(), Integer.parseInt(parts[3]), Integer.parseInt(parts[4]));
        }

        @Override
        protected void onErrorReceived(Throwable throwable) {
            messageBus.unsubscribeSilently(channel, this);
        }

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
