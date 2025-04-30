package com.xhield.parser.mavenpomparser.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.maven.repository.internal.DefaultArtifactDescriptorReader;
import org.apache.maven.repository.internal.DefaultVersionRangeResolver;
import org.apache.maven.repository.internal.DefaultVersionResolver;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.impl.VersionRangeResolver;
import org.eclipse.aether.impl.VersionResolver;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer;
import org.springframework.data.mongodb.core.messaging.MessageListenerContainer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
@EnableScheduling
public class RepositoryConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public RepositorySystem repositorySystem() {

        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();

        // Register all necessary services
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

        // Add the missing services
        locator.addService(ArtifactDescriptorReader.class, DefaultArtifactDescriptorReader.class);
        locator.addService(VersionResolver.class, DefaultVersionResolver.class);
        locator.addService(VersionRangeResolver.class, DefaultVersionRangeResolver.class);

        // Add error handler
        locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
            @Override
            public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
                log.error("Service creation failed for " + type.getName() + " with implementation " + impl.getName());
                exception.printStackTrace();
            }
        });

        return locator.getService(RepositorySystem.class);
    }

    @Bean
    public RepositorySystemSession repositorySystemSession(RepositorySystem repositorySystem) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

//        LocalRepository localRepo = new LocalRepository(System.getProperty("user.home") + "/.m2/repository");
        LocalRepository localRepo = new LocalRepository("target/local-repo");
        session.setLocalRepositoryManager(
                repositorySystem.newLocalRepositoryManager(session, localRepo)
        );

        // Configure session properties
        session.setChecksumPolicy("warn");
        session.setUpdatePolicy("daily");

        // Configure session
//        session.setOffline(false);  // Allow online repository access
//        session.setIgnoreArtifactDescriptorRepositories(false);

        return session;
    }

    @Bean
    public List<RemoteRepository> remoteRepositories() {
        List<RemoteRepository> repositories = new ArrayList<>();

        // Add Maven Central
        repositories.add(new RemoteRepository.Builder(
                "central",
                "default",
                "https://repo1.maven.org/maven2/"
        ).build());

        // Add additional repositories if needed
        repositories.add(new RemoteRepository.Builder(
                "spring-releases",
                "default",
                "https://repo.spring.io/release"
        ).build());

        return repositories;
    }

    @Bean
    public MessageListenerContainer messageListenerContainer(MongoTemplate mongoTemplate) {
        return new DefaultMessageListenerContainer(mongoTemplate);
    }
}
