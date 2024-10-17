/**
 * Copyright © 2016-2024 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.service.entitiy.dashboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.ResourceType;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.msg.queue.ServiceType;
import org.thingsboard.server.dao.resource.ResourceService;
import org.thingsboard.server.dao.widget.WidgetsBundleService;
import org.thingsboard.server.queue.discovery.PartitionService;
import org.thingsboard.server.queue.util.AfterStartUp;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.sync.GitSyncService;
import org.thingsboard.server.service.sync.vc.GitRepository.FileType;
import org.thingsboard.server.service.sync.vc.GitRepository.RepoFile;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Service
@TbCoreComponent
@RequiredArgsConstructor
@Slf4j
public class DashboardSyncService {

    private final GitSyncService gitSyncService;
    private final ResourceService resourceService;
    private final WidgetsBundleService widgetsBundleService;
    private final PartitionService partitionService;

    @Value("${transport.gateway.dashboard.sync.enabled:true}")
    private boolean enabled;
    @Value("${transport.gateway.dashboard.sync.repository_url:}")
    private String repoUrl;
    @Value("${transport.gateway.dashboard.sync.branch:main}")
    private String branch;
    @Value("${transport.gateway.dashboard.sync.fetch_frequency:24}")
    private int fetchFrequencyHours;

    private static final String REPO_KEY = "gateways-dashboard";
    private static final String GATEWAYS_DASHBOARD_KEY = "gateways_dashboard.json";

    @AfterStartUp(order = AfterStartUp.REGULAR_SERVICE)
    public void init() throws Exception {
        if (!enabled) {
            return;
        }
        gitSyncService.registerSync(REPO_KEY, repoUrl, branch, TimeUnit.HOURS.toMillis(fetchFrequencyHours), this::update);
    }

    private void update() {
        if (!partitionService.isMyPartition(ServiceType.TB_CORE, TenantId.SYS_TENANT_ID, TenantId.SYS_TENANT_ID)) {
            return;
        }

        List<RepoFile> resources = listFiles("resources");
        for (RepoFile resourceFile : resources) {
            String data = getFileContent(resourceFile.path());
            resourceService.updateSystemResource(ResourceType.JS_MODULE, resourceFile.name(), data);
        }

        Stream<String> widgetsBundles = listFiles("widget_bundles").stream()
                .map(widgetsBundleFile -> getFileContent(widgetsBundleFile.path()));
        Stream<String> widgetTypes = listFiles("widget_types").stream()
                .map(widgetTypeFile -> getFileContent(widgetTypeFile.path()));
        widgetsBundleService.updateSystemWidgets(widgetsBundles, widgetTypes);

        RepoFile dashboardFile = listFiles("dashboards").get(0);
        String dashboardJson = getFileContent(dashboardFile.path());
        resourceService.updateSystemResource(ResourceType.DASHBOARD, GATEWAYS_DASHBOARD_KEY, dashboardJson);

        log.info("Gateways dashboard sync completed");
    }

    private List<RepoFile> listFiles(String path) {
        return gitSyncService.listFiles(REPO_KEY, path, 1, FileType.FILE);
    }

    private String getFileContent(String path) {
        return gitSyncService.getFileContent(REPO_KEY, path);
    }

}