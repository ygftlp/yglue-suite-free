package org.yglue.flow.orch.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.yglue.flow.orch.domain.Project;
import org.yglue.flow.orch.persistence.mapper.ProjectMapper;

import java.util.List;

/**
 * 项目服务类
 * <p>
 * 提供项目的创建、查询、确保存在等核心功能。
 * </p>
 *
 * @author yglue
 * @since 1.0
 */
@Service
public class ProjectService {

    private final ProjectMapper projectMapper;

    /**
     * 构造函数
     *
     * @param projectMapper 项目数据访问对象
     */
    public ProjectService(ProjectMapper projectMapper) {
        this.projectMapper = projectMapper;
    }

    /**
     * 创建项目
     * <p>
     * 如果项目已存在，将抛出冲突异常。
     * </p>
     *
     * @param key 项目标识（唯一键）
     * @param name 项目名称（可选，如果为空则使用 key）
     * @return 创建的项目对象
     * @throws ResponseStatusException 如果项目已存在，抛出 409 CONFLICT
     */
    @Transactional
    public Project create(String key, String name) {
        if (projectMapper.selectByKey(key) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project already exists: " + key);
        }
        return doCreate(key, name);
    }

    /**
     * 确保结果记录
     * <p>
     * 用于返回项目对象和是否新创建的标志。
     * </p>
     *
     * @param project 项目对象
     * @param created 是否新创建（true 表示新创建，false 表示已存在）
     */
    public record EnsureResult(Project project, boolean created) {}

    /**
     * 确保项目存在
     * <p>
     * 如果项目不存在则创建，存在则返回现有项目。
     * </p>
     *
     * @param key 项目标识
     * @param name 项目名称（可选）
     * @return 项目对象
     */
    @Transactional
    public Project ensureProject(String key, String name) {
        return ensureProjectWithFlag(key, name).project();
    }

    /**
     * 确保项目存在（带创建标志）
     * <p>
     * 如果项目不存在则创建，存在则返回现有项目，并返回是否新创建的标志。
     * </p>
     *
     * @param key 项目标识
     * @param name 项目名称（可选）
     * @return 确保结果，包含项目对象和是否新创建的标志
     */
    @Transactional
    public EnsureResult ensureProjectWithFlag(String key, String name) {
        Project existing = projectMapper.selectByKey(key);
        if (existing != null) {
            return new EnsureResult(existing, false);
        }
        Project created = doCreate(key, name);
        return new EnsureResult(created, true);
    }

    /**
     * 执行项目创建
     *
     * @param key 项目标识
     * @param name 项目名称（如果为空则使用 key）
     * @return 创建的项目对象
     */
    private Project doCreate(String key, String name) {
        Project project = new Project();
        project.setKey(key);
        project.setName((name == null || name.isBlank()) ? key : name);
        projectMapper.insert(project);
        return project;
    }

    /**
     * 查询所有项目
     *
     * @return 项目列表
     */
    public List<Project> listAll() {
        return projectMapper.selectAll();
    }

    /**
     * 根据标识查询项目
     *
     * @param key 项目标识
     * @return 项目对象，如果不存在则返回 null
     */
    public Project findByKey(String key) {
        return projectMapper.selectByKey(key);
    }

    /**
     * 要求项目必须存在
     * <p>
     * 如果项目不存在，将抛出异常。
     * </p>
     *
     * @param key 项目标识
     * @return 项目对象
     * @throws ResponseStatusException 如果项目不存在，抛出 404 NOT_FOUND
     */
    public Project requireProject(String key) {
        Project project = findByKey(key);
        if (project == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found: " + key);
        }
        return project;
    }
}
