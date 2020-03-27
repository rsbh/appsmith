package com.appsmith.server.configurations;

import com.appsmith.server.domains.Application;
import com.appsmith.server.domains.Organization;
import com.appsmith.server.domains.OrganizationPlugin;
import com.appsmith.server.domains.Page;
import com.appsmith.server.domains.Plugin;
import com.appsmith.server.domains.PluginType;
import com.appsmith.server.domains.User;
import com.appsmith.server.domains.UserState;
import com.appsmith.server.repositories.ApplicationRepository;
import com.appsmith.server.repositories.OrganizationRepository;
import com.appsmith.server.repositories.PageRepository;
import com.appsmith.server.repositories.PluginRepository;
import com.appsmith.server.repositories.UserRepository;
import com.github.mongobee.exception.MongobeeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
public class SeedMongoData {

    @Autowired
    private MongoConfig mongoConfig;

    @Bean
    ApplicationRunner init(UserRepository userRepository,
                           OrganizationRepository organizationRepository,
                           ApplicationRepository applicationRepository,
                           PageRepository pageRepository,
                           PluginRepository pluginRepository)
            throws MongobeeException {

        mongoConfig.runMigrations();

        log.info("Seeding the data");
        Object[][] userData = {
                {"user test", "usertest@usertest.com", UserState.ACTIVATED},
                {"api_user", "api_user", UserState.ACTIVATED},
        };
        Object[][] orgData = {
                {"Spring Test Organization", "appsmith-spring-test.com", "appsmith.com", "spring-test-organization"}
        };
        Object[][] appData = {
                {"LayoutServiceTest TestApplications"}
        };
        Object[][] pageData = {
                {"validPageName"}
        };
        Object[][] pluginData = {
                {"Installed Plugin Name", PluginType.API, "installed-plugin"},
                {"Not Installed Plugin Name", PluginType.API, "not-installed-plugin"}
        };
        return args -> {
            organizationRepository.deleteAll()
                    .thenMany(
                            // Seed the plugin data into the DB
                            Flux.just(pluginData)
                                    .map(array -> {
                                        Plugin plugin = new Plugin();
                                        plugin.setName((String) array[0]);
                                        plugin.setType((PluginType) array[1]);
                                        plugin.setPackageName((String) array[2]);
                                        return plugin;
                                    }).flatMap(pluginRepository::save)
                    )
                    .then(pluginRepository.findByName((String) pluginData[0][0]))
                    .map(plugin -> plugin.getId())
                    .flatMapMany(pluginId ->
                            // Seed the organization data into the DB
                            Flux.just(orgData)
                                    .map(array -> {
                                        Organization organization = new Organization();
                                        organization.setName((String) array[0]);
                                        organization.setDomain((String) array[1]);
                                        organization.setWebsite((String) array[2]);
                                        organization.setSlug((String) array[3]);
                                        OrganizationPlugin orgPlugin = new OrganizationPlugin();
                                        orgPlugin.setPluginId(pluginId);
                                        List<OrganizationPlugin> orgPlugins = new ArrayList<>();
                                        orgPlugins.add(orgPlugin);
                                        organization.setPlugins(orgPlugins);
                                        return organization;
                                    }).flatMap(organizationRepository::save)
                    )
                    // Query the seed data to get the organizationId (required for application creation)
                    .then(organizationRepository.findBySlug((String) orgData[0][3]))
                    .map(org -> org.getId())
                    // Seed the user data into the DB
                    .flatMapMany(orgId -> Flux.just(userData)
                            .map(array -> {
                                User user = new User();
                                user.setName((String) array[0]);
                                user.setEmail((String) array[1]);
                                user.setState((UserState) array[2]);
                                user.setCurrentOrganizationId(orgId);
                                return user;
                            })
                            .flatMap(userRepository::save)
                            .then(Mono.just(orgId))
                    ).flatMap(orgId ->
                            // Seed the application data into the DB
                            Flux.just(appData).map(array -> {
                                Application app = new Application();
                                app.setName((String) array[0]);
                                app.setOrganizationId(orgId);
                                return app;
                            }).flatMap(applicationRepository::save)
                    // Query the seed data to get the applicationId (required for page creation)
            ).then(applicationRepository.findByName((String) appData[0][0]))
                    .map(application -> application.getId())
                    .flatMapMany(appId -> Flux.just(pageData)
                            // Seed the page data into the DB
                            .map(array -> {
                                Page page = new Page();
                                page.setName((String) array[0]);
                                page.setApplicationId(appId);
                                return page;
                            })
                            .flatMap(pageRepository::save)
                    )
                    .blockLast();
        };
    }
}
