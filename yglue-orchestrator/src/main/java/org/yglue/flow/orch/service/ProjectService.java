package org.yglue.flow.orch.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.yglue.flow.orch.domain.Project;
import org.yglue.flow.orch.persistence.mapper.ProjectMapper;

import java.util.List;

@Service
public class ProjectService {

    private final ProjectMapper projectMapper;

    public ProjectService(ProjectMapper projectMapper) {
        this.projectMapper = projectMapper;
    }

    @Transactional
    public Project create(String key, String name) {
        if (projectMapper.selectByKey(key) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project already exists: " + key);
        }
        return doCreate(key, name);
    }

    public record EnsureResult(Project project, boolean created) {}

    @Transactional
    public Project ensureProject(String key, String name) {
        return ensureProjectWithFlag(key, name).project();
    }

    @Transactional
    public EnsureResult ensureProjectWithFlag(String key, String name) {
        Project existing = projectMapper.selectByKey(key);
        if (existing != null) {
            return new EnsureResult(existing, false);
        }
        Project created = doCreate(key, name);
        return new EnsureResult(created, true);
    }

    private Project doCreate(String key, String name) {
        Project project = new Project();
        project.setKey(key);
        project.setName((name == null || name.isBlank()) ? key : name);
        projectMapper.insert(project);
        return project;
    }

    public List<Project> listAll() {
        return projectMapper.selectAll();
    }

    public Project findByKey(String key) {
        return projectMapper.selectByKey(key);
    }

    public Project requireProject(String key) {
        Project project = findByKey(key);
        if (project == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found: " + key);
        }
        return project;
    }
}
