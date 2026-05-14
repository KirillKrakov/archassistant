package com.example.archassistant.controller

import com.example.archassistant.dto.generatedfiles.request.GeneratedFileSyncRequest
import com.example.archassistant.dto.generatedfiles.response.GeneratedFileSyncResponse
import com.example.archassistant.dto.generatedfiles.response.GeneratedFilesClearResponse
import com.example.archassistant.service.generatedfiles.GeneratedFilesSyncService
import com.example.archassistant.service.generatedfiles.ProjectOperationLockService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/generated-files")
@CrossOrigin(origins = ["*"])
class GeneratedFilesController(
    private val generatedFilesSyncService: GeneratedFilesSyncService,
    private val operationLockService: ProjectOperationLockService
) {

    @PostMapping("/{projectId}/sync")
    fun syncGeneratedFiles(
        @PathVariable projectId: String,
        @RequestBody request: GeneratedFileSyncRequest
    ): ResponseEntity<GeneratedFileSyncResponse> {
        return operationLockService.withLock(projectId) {
            val result = generatedFilesSyncService.syncGeneratedFiles(projectId, request)
            if (!result.success) {
                ResponseEntity.badRequest().body(result)
            } else {
                ResponseEntity.ok(result)
            }
        }
    }

    @DeleteMapping("/{projectId}")
    fun clearOverlay(@PathVariable projectId: String): ResponseEntity<GeneratedFilesClearResponse> {
        return operationLockService.withLock(projectId) {
            val result = generatedFilesSyncService.clearOverlay(projectId)
            if (result.success) {
                ResponseEntity.ok(result)
            } else {
                ResponseEntity.internalServerError().body(result)
            }
        }
    }
}