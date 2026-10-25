package com.axenix.javablueprints.repository

import com.github.javaparser.ast.CompilationUnit
import java.util.concurrent.ConcurrentHashMap

data class ProjectData(
    val originalFiles: MutableMap<String, String>,
    val currentFiles: MutableMap<String, String>,
    val compilationUnits: MutableMap<String, CompilationUnit>,
    val mainFile: String
)

object ProjectRepository {
    private val projects = ConcurrentHashMap<String, ProjectData>()
    private var currentProjectId: String = "default"
    
    fun createProject(projectId: String, files: Map<String, String>, mainFile: String) {
        projects[projectId] = ProjectData(
            originalFiles = files.toMutableMap(),
            currentFiles = files.toMutableMap(),
            compilationUnits = mutableMapOf(),
            mainFile = mainFile
        )
        currentProjectId = projectId
    }
    
    fun getProject(projectId: String = currentProjectId): ProjectData? {
        return projects[projectId]
    }
    
    fun updateFiles(projectId: String = currentProjectId, files: Map<String, String>) {
        projects[projectId]?.currentFiles?.putAll(files)
    }
    
    fun updateCompilationUnit(projectId: String = currentProjectId, filePath: String, cu: CompilationUnit) {
        projects[projectId]?.compilationUnits?.put(filePath, cu)
    }
    
    fun getOriginalFiles(projectId: String = currentProjectId): Map<String, String> {
        return projects[projectId]?.originalFiles ?: emptyMap()
    }
    
    fun getCurrentFiles(projectId: String = currentProjectId): Map<String, String> {
        return projects[projectId]?.currentFiles ?: emptyMap()
    }
    
    fun getCompilationUnit(projectId: String = currentProjectId, filePath: String): CompilationUnit? {
        return projects[projectId]?.compilationUnits?.get(filePath)
    }
}
