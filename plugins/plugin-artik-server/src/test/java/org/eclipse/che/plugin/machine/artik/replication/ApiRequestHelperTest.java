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

import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.model.machine.Machine;
import org.eclipse.che.api.core.model.machine.MachineConfig;
import org.eclipse.che.api.core.model.machine.MachineSource;
import org.eclipse.che.api.core.rest.HttpJsonRequest;
import org.eclipse.che.api.core.rest.HttpJsonRequestFactory;
import org.eclipse.che.api.core.rest.HttpJsonResponse;
import org.eclipse.che.api.machine.shared.dto.MachineConfigDto;
import org.eclipse.che.api.machine.shared.dto.MachineDto;
import org.eclipse.che.api.machine.shared.dto.MachineSourceDto;
import org.eclipse.che.api.machine.shared.dto.recipe.RecipeDescriptor;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Random;

import static java.lang.Integer.MAX_VALUE;
import static java.lang.System.currentTimeMillis;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/**
 * Tests for {@link ApiRequestHelper}
 *
 * @author Dmitry Kuleshov
 * @since 4.5
 */
@Listeners(value = {MockitoTestNGListener.class})
public class ApiRequestHelperTest {
    private static final String MACHINE_ID        = Integer.toString(new Random(currentTimeMillis()).nextInt(MAX_VALUE));
    private static final String RECIPE_ID         = Integer.toString(new Random(currentTimeMillis()).nextInt(MAX_VALUE));
    private static final String MACHINE_ELEMENT   = "machine";
    private static final String WORKSPACE_ELEMENT = "workspace";
    private static final String SEPARATOR         = "/";
    private static final String RECIPE_ELEMENT    = "recipe";
    private static final String SCRIPT_ELEMENT    = "script";
    private static final String API_ENDPOINT      = "http://localhost:8080/wsmaster/api";

    @Mock
    private HttpJsonRequestFactory httpJsonRequestFactory;
    @Mock
    private HttpJsonResponse       httpJsonResponse;
    @Mock
    private HttpJsonRequest        httpJsonRequest;
    @Mock
    private MachineSource          machineSource;
    @Mock
    private MachineSourceDto       machineSourceDto;
    @Mock
    private MachineConfig          machineConfig;
    @Mock
    private MachineConfigDto       machineConfigDto;
    @Mock
    private Machine                machine;
    @Mock
    private MachineDto             machineDto;


    private ApiRequestHelper apiRequestHelper;


    @BeforeMethod
    public void beforeMethod() throws IOException, ApiException {
        apiRequestHelper = new ApiRequestHelper(API_ENDPOINT, httpJsonRequestFactory);

        when(httpJsonResponse.asDto(RecipeDescriptor.class)).thenReturn(mock(RecipeDescriptor.class));
        when(httpJsonResponse.asDto(Machine.class)).thenReturn(machine);
        when(httpJsonResponse.asDto(MachineDto.class)).thenReturn(machineDto);
        when(httpJsonRequest.useGetMethod()).thenReturn(httpJsonRequest);
        when(httpJsonRequest.request()).thenReturn(httpJsonResponse);
        when(machineSource.getLocation()).thenReturn(SEPARATOR + RECIPE_ELEMENT + SEPARATOR + RECIPE_ID + SEPARATOR + SCRIPT_ELEMENT);
        when(machineSourceDto.getLocation()).thenReturn(SEPARATOR + RECIPE_ELEMENT + SEPARATOR + RECIPE_ID + SEPARATOR + SCRIPT_ELEMENT);
        when(machineConfig.getSource()).thenReturn(machineSource);
        when(machineConfigDto.getSource()).thenReturn(machineSourceDto);
        when(machine.getConfig()).thenReturn(machineConfig);
        when(machineDto.getConfig()).thenReturn(machineConfigDto);
        when(httpJsonRequestFactory.fromUrl(any())).thenReturn(httpJsonRequest);


    }

    @Test
    public void shouldSendProperRequestWhenGetRecipe() throws IOException, ApiException {
        apiRequestHelper.getRecipe(machineSource);
        verify(httpJsonRequestFactory, times(1)).fromUrl(eq(API_ENDPOINT + SEPARATOR + RECIPE_ELEMENT + SEPARATOR + RECIPE_ID));
    }

    @Test
    public void shouldSendProperRequestWhenGetMachines() throws IOException, ApiException {
        apiRequestHelper.getMachines();
        verify(httpJsonRequestFactory, times(1))
                .fromUrl(eq(API_ENDPOINT + SEPARATOR + WORKSPACE_ELEMENT + SEPARATOR + SEPARATOR + MACHINE_ELEMENT));
    }

    @Test
    public void shouldSendProperRequestWhenGetMachineById() throws IOException, ApiException {
        apiRequestHelper.getMachine(MACHINE_ID);
        verify(httpJsonRequestFactory, times(1)).fromUrl(
                eq(API_ENDPOINT + SEPARATOR + WORKSPACE_ELEMENT + SEPARATOR + SEPARATOR + MACHINE_ELEMENT + SEPARATOR + MACHINE_ID));
    }

    @Test
    public void shouldSendProperRequestWhenGetScriptByMachine() throws IOException, ApiException {
        apiRequestHelper.getScript(machine);
        verify(httpJsonRequestFactory, times(1)).fromUrl(eq(API_ENDPOINT + SEPARATOR + RECIPE_ELEMENT + SEPARATOR + RECIPE_ID));
    }

    @Test
    public void shouldSendProperRequestWhenGetScriptByMachineId() throws IOException, ApiException {
        apiRequestHelper.getScript(MACHINE_ID);
        verify(httpJsonRequestFactory, times(1)).fromUrl(
                eq(API_ENDPOINT + SEPARATOR + WORKSPACE_ELEMENT + SEPARATOR + SEPARATOR + MACHINE_ELEMENT + SEPARATOR + MACHINE_ID));
        verify(httpJsonRequestFactory, times(1)).fromUrl(eq(API_ENDPOINT + SEPARATOR + RECIPE_ELEMENT + SEPARATOR + RECIPE_ID));
    }
}
