package com.xhield.parser.mavenpomparser.service;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.ModelSource;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.model.resolution.UnresolvableModelException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class AetherModelResolver implements ModelResolver {
    private final RepositorySystem repoSystem;
    private final RepositorySystemSession session;
    private final List<RemoteRepository> repositories;

    public AetherModelResolver(RepositorySystem repoSystem,
                               RepositorySystemSession session,
                               List<RemoteRepository> repositories) {
        this.repoSystem = repoSystem;
        this.session = session;
        this.repositories = new ArrayList<>(repositories);
    }

    @Override
    public ModelSource resolveModel(String groupId, String artifactId, String version) throws UnresolvableModelException {
        String coords = String.format("%s:%s:pom:%s", groupId, artifactId, version);
        Artifact pomArtifact = new DefaultArtifact(coords);

        ArtifactRequest request = new ArtifactRequest(pomArtifact, repositories, null);
        try {
            ArtifactResult result = repoSystem.resolveArtifact(session, request);
            File pomFile = result.getArtifact().getFile();

            return new ModelSource() {
                @Override
                public InputStream getInputStream() throws java.io.IOException {
                    return new FileInputStream(pomFile);
                }

                @Override
                public String getLocation() {
                    return pomFile.getAbsolutePath();
                }
            };
        } catch (Exception e) {
            throw new UnresolvableModelException(
                    "Failed to resolve POM for " + coords,
                    groupId, artifactId, version, e);
        }
    }

    @Override
    public ModelSource resolveModel(Parent parent) throws UnresolvableModelException {
        return resolveModel(parent.getGroupId(), parent.getArtifactId(), parent.getVersion());
    }

    @Override
    public ModelSource resolveModel(Dependency dependency) throws UnresolvableModelException {
        return resolveModel(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion());
    }

    @Override
    public void addRepository(Repository repository) {
        addRepository(repository, false);
    }

    @Override
    public void addRepository(Repository repository, boolean replace) {
        RemoteRepository remoteRepo = new RemoteRepository.Builder(
                repository.getId(),
                "default",
                repository.getUrl())
                .setReleasePolicy(new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_DAILY, RepositoryPolicy.CHECKSUM_POLICY_WARN))
                .setSnapshotPolicy(new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_DAILY, RepositoryPolicy.CHECKSUM_POLICY_WARN))
                .build();

        if (replace) {
            repositories.removeIf(r -> r.getId().equals(repository.getId()));
        }

        repositories.add(remoteRepo);
    }

    @Override
    public ModelResolver newCopy() {
        return new AetherModelResolver(repoSystem, session, repositories);
    }
}
