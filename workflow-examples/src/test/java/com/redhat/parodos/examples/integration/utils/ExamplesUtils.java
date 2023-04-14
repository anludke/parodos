
package com.redhat.parodos.examples.integration.utils;

import com.redhat.parodos.sdk.api.ProjectApi;
import com.redhat.parodos.sdk.invoker.ApiCallback;
import com.redhat.parodos.sdk.invoker.ApiClient;
import com.redhat.parodos.sdk.invoker.ApiException;
import com.redhat.parodos.sdk.model.ProjectRequestDTO;
import com.redhat.parodos.sdk.model.ProjectResponseDTO;
import com.redhat.parodos.sdk.model.WorkFlowStatusResponseDTO;
import com.redhat.parodos.workflows.work.WorkStatus;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.util.Strings;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Gloria Ciavarrini (Github: gciavarrini)
 */
@Slf4j
public final class ExamplesUtils {

	public static void waitProjectStart(ProjectApi projectApi) throws ApiException, InterruptedException {
		AsyncWaitProjectResult asyncResult = new AsyncWaitProjectResult();
		Lock lock = new ReentrantLock();
		Condition response = lock.newCondition();
		ApiCallback<List<ProjectResponseDTO>> apiCallback = new ApiCallback<>() {
			@Override
			public void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
				try {
					projectApi.getProjectsAsync(this);
				}
				catch (ApiException apie) {
					asyncResult.setError(apie.getMessage());
					signal();
				}
			}

			@Override
			public void onSuccess(List<ProjectResponseDTO> result, int statusCode,
					Map<String, List<String>> responseHeaders) {
				signal();
			}

			@Override
			public void onUploadProgress(long bytesWritten, long contentLength, boolean done) {
			}

			@Override
			public void onDownloadProgress(long bytesRead, long contentLength, boolean done) {
			}

			private void signal() {
				lock.lock();
				try {
					response.signal();
				}
				finally {
					lock.unlock();
				}
			}
		};
		projectApi.getProjectsAsync(apiCallback);
		lock.lock();
		try {
			// should be more than enough
			response.await(60, TimeUnit.SECONDS);
			if (asyncResult.getError() != null) {
				fail("An error occurred while executing getProjectAsync: " + asyncResult.getError());
			}
		}
		finally {
			lock.unlock();
		}
	}

	public static WorkFlowStatusResponseDTO waitAsyncStatusResponse(WorkflowApi workflowApi, String workFlowExecutionId)
			throws ApiException, InterruptedException {
		AsyncStatusResult asyncResult = new AsyncStatusResult();
		Lock lock = new ReentrantLock();
		Condition response = lock.newCondition();
		ApiCallback<WorkFlowStatusResponseDTO> apiCallback = new ApiCallback<>() {

			@Override
			public void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
				System.out.println("onFAILURE");
				try {
					workflowApi.getStatusAsync(workFlowExecutionId, this);
				}
				catch (ApiException apie) {
					asyncResult.setError(apie.getMessage());
					signal();
				}
			}

			@Override
			public void onSuccess(WorkFlowStatusResponseDTO result, int statusCode,
					Map<String, List<String>> responseHeaders) {
				if (!result.getStatus().equals(WorkStatus.COMPLETED.toString())) {
					try {
						workflowApi.getStatusAsync(workFlowExecutionId, this);
					}
					catch (ApiException apie) {
						asyncResult.setError(apie.getMessage());
						signal();
					}
				}
				else {
					asyncResult.setStatusCode(statusCode);
					asyncResult.setResult(result);
					asyncResult.setError(null);
					signal();
				}

			}

			@Override
			public void onUploadProgress(long bytesWritten, long contentLength, boolean done) {
			}

			@Override
			public void onDownloadProgress(long bytesRead, long contentLength, boolean done) {
			}

			private void signal() {
				lock.lock();
				try {
					response.signal();
				}
				finally {
					lock.unlock();
				}
			}
		};
		workflowApi.getStatusAsync(workFlowExecutionId, apiCallback);
		lock.lock();
		try {
			// should be more than enough
			response.await(60, TimeUnit.SECONDS);

			if (asyncResult.getError() != null) {
				fail("An error occurred while executing waitAsyncStatusResponse: " + asyncResult.getError());
			}
		}
		finally {
			lock.unlock();
		}
		return asyncResult.getResult();
	}

	@Nullable
	public static ProjectResponseDTO getProjectByNameAndDescription(List<ProjectResponseDTO> projects,
			String projectName, String projectDescription) {
		return projects.stream()
				.filter(prj -> projectName.equals(prj.getName()) && projectDescription.equals(prj.getDescription())
						&& prj.getUsername() == null && !Strings.isNullOrEmpty(prj.getId()))
				.findAny().orElse(null);
	}

	@Data
	private static class AsyncWaitProjectResult {

		private String error;

	}

	@Data
	private static class AsyncStatusResult {

		WorkFlowStatusResponseDTO result;

		int statusCode;

		private String error;

	}

	public static ProjectResponseDTO commonProjectAPI(ApiClient apiClient, String projectName,
			String projectDescription) throws InterruptedException, ApiException {
		ProjectApi projectApi = new ProjectApi(apiClient);

		waitProjectStart(projectApi);
		log.info("Project is ✔️ on {}", apiClient.getBasePath());

		ProjectResponseDTO testProject;

		// RETRIEVE ALL PROJECTS AVAILABLE
		log.info("Get all available projects");
		List<ProjectResponseDTO> projects = projectApi.getProjects();
		// CHECK IF testProject ALREADY EXISTS
		testProject = getProjectByNameAndDescription(projects, projectName, projectDescription);

		// CREATE PROJECT "Test Project Name" IF NOT EXISTS
		if (testProject == null) {
			log.info("There are no projects. Creating project {}", projectName);
			// DEFINE A TEST PROJECT REQUEST
			ProjectRequestDTO projectRequestDTO = new ProjectRequestDTO();
			projectRequestDTO.setName(projectName);
			projectRequestDTO.setDescription(projectDescription);

			ProjectResponseDTO projectResponseDTO = projectApi.createProject(projectRequestDTO);
			assertNotNull(projectResponseDTO);
			assertEquals(projectName, projectResponseDTO.getName());
			assertEquals(projectDescription, projectResponseDTO.getDescription());
			log.info("Project {} successfully created", projectName);
		}

		// ASSERT PROJECT "testProject" IS PRESENT
		projects = projectApi.getProjects();
		log.debug("PROJECTS: {}", projects);
		assertTrue(projects.size() > 0);
		testProject = getProjectByNameAndDescription(projects, projectName, projectDescription);
		assertNotNull(testProject);

		return testProject;
	}

}