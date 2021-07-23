// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.acceptance.rest;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.httpd.restapi.RestApiServlet.SC_CLIENT_CLOSED_REQUEST;
import static org.apache.http.HttpStatus.SC_REQUEST_TIMEOUT;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.server.cancellation.RequestCancelledException;
import com.google.gerrit.server.cancellation.RequestStateProvider;
import com.google.gerrit.server.project.CreateProjectArgs;
import com.google.gerrit.server.validators.ProjectCreationValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import org.junit.Test;

public class CancellationIT extends AbstractDaemonTest {
  @Inject private ExtensionRegistry extensionRegistry;

  @Test
  public void handleClientDisconnected() throws Exception {
    ProjectCreationValidationListener projectCreationListener =
        new ProjectCreationValidationListener() {
          @Override
          public void validateNewProject(CreateProjectArgs args) throws ValidationException {
            // Simulate a request cancellation by throwing RequestCancelledException. In contrast to
            // an actual request cancellation this allows us to verify the HTTP status code that is
            // set when a request is cancelled.
            throw new RequestCancelledException(
                RequestStateProvider.Reason.CLIENT_CLOSED_REQUEST, /* cancellationMessage= */ null);
          }
        };
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      RestResponse response = adminRestSession.put("/projects/" + name("new"));
      assertThat(response.getStatusCode()).isEqualTo(SC_CLIENT_CLOSED_REQUEST);
      assertThat(response.getEntityContent()).isEqualTo("Client Closed Request");
    }
  }

  @Test
  public void handleClientDeadlineExceeded() throws Exception {
    ProjectCreationValidationListener projectCreationListener =
        new ProjectCreationValidationListener() {
          @Override
          public void validateNewProject(CreateProjectArgs args) throws ValidationException {
            // Simulate an exceeded deadline by throwing RequestCancelledException.
            throw new RequestCancelledException(
                RequestStateProvider.Reason.CLIENT_PROVIDED_DEADLINE_EXCEEDED,
                /* cancellationMessage= */ null);
          }
        };
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      RestResponse response = adminRestSession.put("/projects/" + name("new"));
      assertThat(response.getStatusCode()).isEqualTo(SC_REQUEST_TIMEOUT);
      assertThat(response.getEntityContent()).isEqualTo("Client Provided Deadline Exceeded");
    }
  }

  @Test
  public void handleServerDeadlineExceeded() throws Exception {
    ProjectCreationValidationListener projectCreationListener =
        new ProjectCreationValidationListener() {
          @Override
          public void validateNewProject(CreateProjectArgs args) throws ValidationException {
            // Simulate an exceeded deadline by throwing RequestCancelledException.
            throw new RequestCancelledException(
                RequestStateProvider.Reason.SERVER_DEADLINE_EXCEEDED,
                /* cancellationMessage= */ null);
          }
        };
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      RestResponse response = adminRestSession.put("/projects/" + name("new"));
      assertThat(response.getStatusCode()).isEqualTo(SC_REQUEST_TIMEOUT);
      assertThat(response.getEntityContent()).isEqualTo("Server Deadline Exceeded");
    }
  }

  @Test
  public void handleRequestCancellationWithMessage() throws Exception {
    ProjectCreationValidationListener projectCreationListener =
        new ProjectCreationValidationListener() {
          @Override
          public void validateNewProject(CreateProjectArgs args) throws ValidationException {
            // Simulate an exceeded deadline by throwing RequestCancelledException.
            throw new RequestCancelledException(
                RequestStateProvider.Reason.SERVER_DEADLINE_EXCEEDED, "deadline = 10m");
          }
        };
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      RestResponse response = adminRestSession.put("/projects/" + name("new"));
      assertThat(response.getStatusCode()).isEqualTo(SC_REQUEST_TIMEOUT);
      assertThat(response.getEntityContent())
          .isEqualTo("Server Deadline Exceeded\n\ndeadline = 10m");
    }
  }
}
