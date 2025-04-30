package com.xhield.parser.mavenpomparser.service;

import com.xhield.parser.mavenpomparser.data.DependencyDetail;
import com.xhield.parser.mavenpomparser.data.DependencyInfo;
import com.xhield.parser.mavenpomparser.data.ScanRequest;
import com.xhield.parser.mavenpomparser.repository.ScanRequestRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.building.*;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PomParserService {

    @Autowired
    private ScanRequestRepository scanRequestRepository;

    private RepositorySystem repositorySystem;
    private RepositorySystemSession session;
    private List<RemoteRepository> repositories;

    @Autowired
    public PomParserService(RepositorySystem repositorySystem, RepositorySystemSession session) {
        this.repositorySystem = repositorySystem;
        this.session = session;
        this.repositories = new ArrayList<>();
        this.repositories.add(new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2").build());
    }

    @Async
    public void processPomAsync(ScanRequest scanRequest, boolean forceScan) {
        try {
            scanRequest.setStatus("PROCESSING");
            scanRequestRepository.save(scanRequest);

            DependencyInfo allDependencies = parsePomContent(scanRequest.getPomContent());

            // Update result and mark as completed
            scanRequest.setStatus("COMPLETED");
            scanRequest.setCompletedAt(LocalDateTime.now());
            scanRequest.setResult(allDependencies);
            scanRequestRepository.save(scanRequest);

            // Later: send WebSocket notification to client that it's done
        } catch (Exception e) {
            scanRequest.setStatus("FAILED");
            scanRequest.setMessage(e.getMessage());
            scanRequest.setCompletedAt(LocalDateTime.now());
            scanRequestRepository.save(scanRequest);

            // Optionally notify client about failure
        }
    }

    public DependencyInfo parsePomContent(String pomContent) throws Exception {
        try (InputStream inputStream = new ByteArrayInputStream(pomContent.getBytes(StandardCharsets.UTF_8));
             InputStreamReader streamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {


            Model model = getEffectivePom(pomContent);

            log.debug("Successfully read POM model from content");

            // Get project coordinates for parent field
            String groupId = model.getGroupId() != null ? model.getGroupId() : model.getParent().getGroupId();
            String artifactId = model.getArtifactId();
            String version = model.getVersion() != null ? model.getVersion() : model.getParent().getVersion();
            String projectParent = String.format("%s:%s:%s", groupId, artifactId, version);

            // Get direct dependencies
            List<DependencyDetail> directDependencies = model.getDependencies().stream()
                    .map(dep -> new DependencyDetail(
                            dep.getGroupId(),
                            dep.getArtifactId(),
                            dep.getVersion(),
                            projectParent,
                            dep.getScope() != null ? dep.getScope() : "compile",
                            (dep != null && dep.isOptional()),
                            0))
                    .collect(Collectors.toList());

            // Get transitive dependencies
            List<DependencyDetail> transitiveDependencies = resolveTransitiveDependencies(model);

            return new DependencyInfo(directDependencies, transitiveDependencies);
        }
    }

    private List<DependencyDetail> resolveTransitiveDependencies(Model model) throws Exception {
        List<org.eclipse.aether.graph.Dependency> aetherDependencies = model.getDependencies().stream()
                .map(dep -> new org.eclipse.aether.graph.Dependency(
                        new DefaultArtifact(dep.getGroupId(), dep.getArtifactId(), "", "jar", dep.getVersion()),
                        dep.getScope()))
                .collect(Collectors.toList());

        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setDependencies(aetherDependencies);
        collectRequest.setRepositories(repositories);

        DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, null);
        DependencyResult dependencyResult = repositorySystem.resolveDependencies(session, dependencyRequest);

        List<DependencyDetail> transitiveDeps = new ArrayList<>();
        DependencyNode root = dependencyResult.getRoot();
        String rootParent = model.getGroupId() + ":" + model.getArtifactId() + ":" + model.getVersion();
        for (DependencyNode child : dependencyResult.getRoot().getChildren()) {
            collectTransitiveDependencies(child, rootParent, transitiveDeps, 1);
        }


//        collectTransitiveDependencies(dependencyResult.getRoot(), null, transitiveDeps, 0);
        return transitiveDeps;
    }

    private void collectTransitiveDependencies(DependencyNode node, String parentInfo,
                                               List<DependencyDetail> deps, int depth) {
        Artifact artifact = node.getArtifact();
        if (artifact != null) {
            String scope = node.getDependency() != null ? node.getDependency().getScope() : "compile";
            boolean optional = node.getDependency() != null && Boolean.TRUE.equals(node.getDependency().isOptional());

            deps.add(new DependencyDetail(
                    artifact.getGroupId(),
                    artifact.getArtifactId(),
                    artifact.getVersion(),
                    parentInfo,
                    scope,
                    optional,
                    depth
            ));

            String thisNodeAsParent = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
            for (DependencyNode child : node.getChildren()) {
                collectTransitiveDependencies(child, thisNodeAsParent, deps, depth + 1);
            }
        }
    }



    public Model getEffectivePom(String pomContent) throws Exception {
        ModelBuildingRequest request = new DefaultModelBuildingRequest();
        request.setProcessPlugins(false);
        request.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
        request.setSystemProperties(System.getProperties());

        request.setModelSource(new InputStreamModelSource(new ByteArrayInputStream(pomContent.getBytes(StandardCharsets.UTF_8))));
        request.setModelResolver(new AetherModelResolver(repositorySystem, session, repositories));

        ModelBuilder builder = new DefaultModelBuilderFactory().newInstance();
        ModelBuildingResult result = builder.build(request);
        return result.getEffectiveModel();
    }

    class InputStreamModelSource implements ModelSource {
        private final InputStream inputStream;

        public InputStreamModelSource(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public InputStream getInputStream() {
            return inputStream;
        }

        @Override
        public String getLocation() {
            return "in-memory-pom.xml";
        }
    }
}
