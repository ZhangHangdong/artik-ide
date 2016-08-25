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
import com.google.inject.name.Named;

import org.eclipse.che.WorkspaceIdProvider;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.model.machine.Machine;
import org.eclipse.che.api.core.model.machine.MachineConfig;
import org.eclipse.che.api.core.model.machine.MachineSource;
import org.eclipse.che.api.core.rest.HttpJsonRequestFactory;
import org.eclipse.che.api.environment.server.MachineService;
import org.eclipse.che.api.machine.server.recipe.RecipeService;
import org.eclipse.che.api.machine.shared.dto.MachineDto;
import org.eclipse.che.api.machine.shared.dto.recipe.RecipeDescriptor;
import org.slf4j.Logger;

import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static java.util.regex.Pattern.*;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Simple API request helper, assists in calling {@link MachineService} and
 * {@link RecipeService}.
 *
 * @author Dmitry Kuleshov
 *
 * @since 4.5
 */
@Beta
@Singleton
public class ApiRequestHelper {
    private static final Logger LOG = getLogger(ApiRequestHelper.class);

    private static final Pattern RECIPE_ID_GROUP_REGEXP = compile(".*/recipe/(.*)/script");

    private final String                 apiEndpoint;
    private final HttpJsonRequestFactory httpJsonRequestFactory;

    @Inject
    public ApiRequestHelper(@Named("api.endpoint") String apiEndpoint, HttpJsonRequestFactory httpJsonRequestFactory) {
        this.apiEndpoint = apiEndpoint;
        this.httpJsonRequestFactory = httpJsonRequestFactory;
    }

    public String getScript(Machine machine) throws IOException, ApiException {
        final MachineConfig config = machine.getConfig();
        final MachineSource source = config.getSource();
        return getRecipe(source).getScript();
    }

    public List<MachineDto> getMachines() throws IOException, ApiException {
        final String href = UriBuilder.fromUri(apiEndpoint)
                                      .path(MachineService.class)
                                      .path(MachineService.class, "getMachines")
                                      .build(WorkspaceIdProvider.getWorkspaceId())
                                      .toString();

        return httpJsonRequestFactory.fromUrl(href)
                                     .useGetMethod()
                                     .request()
                                     .asList(MachineDto.class);
    }


    public RecipeDescriptor getRecipe(MachineSource source) throws IOException, ApiException {
        final String href = UriBuilder.fromUri(apiEndpoint)
                                      .path(RecipeService.class)
                                      .path(RecipeService.class, "getRecipe")
                                      .build(extractRecipeId(source.getLocation()))
                                      .toString();

        return httpJsonRequestFactory.fromUrl(href)
                                     .useGetMethod()
                                     .request()
                                     .asDto(RecipeDescriptor.class);
    }

    public String getScript(String machineId) throws IOException, ApiException {
        return getScript(getMachine(machineId));
    }

    public MachineDto getMachine(String machineId) throws IOException, ApiException {
        final String href = UriBuilder.fromUri(apiEndpoint)
                                      .path(MachineService.class)
                                      .path(MachineService.class, "getMachineById")
                                      .build(WorkspaceIdProvider.getWorkspaceId(), machineId)
                                      .toString();

        return httpJsonRequestFactory.fromUrl(href)
                                     .useGetMethod()
                                     .request()
                                     .asDto(MachineDto.class);
    }

    private String extractRecipeId(String location) {
        final Matcher matcher = RECIPE_ID_GROUP_REGEXP.matcher(location);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            LOG.error("Malformed location parameter: '{}'", location);
            throw new IllegalArgumentException(format("Malformed location parameter: '%s'", location));
        }
    }
}
