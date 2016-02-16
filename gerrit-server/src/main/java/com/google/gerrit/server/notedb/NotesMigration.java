// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

/**
 * Holds the current state of the NoteDb migration.
 * <p>
 * The migration will proceed one root entity type at a time. A <em>root
 * entity</em> is an entity stored in ReviewDb whose key's
 * {@code getParentKey()} method returns null. For an example of the entity
 * hierarchy rooted at Change, see the diagram in
 * {@code com.google.gerrit.reviewdb.client.Change}.
 * <p>
 * During a transitional period, each root entity group from ReviewDb may be
 * either <em>written to</em> or <em>both written to and read from</em> NoteDb.
 * <p>
 * This class controls the state of the migration according to options in
 * {@code gerrit.config}. In general, any changes to these options should only
 * be made by adventurous administrators, who know what they're doing, on
 * non-production data, for the purposes of testing the NoteDb implementation.
 * Changing options quite likely requires re-running {@code RebuildNoteDb}. For
 * these reasons, the options remain undocumented.
 */
public abstract class NotesMigration {
  public abstract boolean readChanges();

  public abstract boolean writeChanges();

  public boolean enabled() {
    return writeChanges()
        || readChanges();
  }
}
