// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.index.ChangeField.LEGACY_REVIEWED;
import static com.google.gerrit.server.index.ChangeField.REVIEWEDBY;

import com.google.common.base.Optional;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.index.ChangeField;
import com.google.gerrit.server.index.FieldDef;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gwtorm.server.OrmException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

class IsReviewedPredicate extends IndexPredicate<ChangeData> {
  private static final Account.Id NOT_REVIEWED =
      new Account.Id(ChangeField.NOT_REVIEWED);

  @SuppressWarnings("deprecation")
  static Predicate<ChangeData> create(Schema<ChangeData> schema) {
    if (getField(schema) == LEGACY_REVIEWED) {
      return new LegacyIsReviewedPredicate();
    }
    return Predicate.not(new IsReviewedPredicate(NOT_REVIEWED));
  }

  @SuppressWarnings("deprecation")
  static Predicate<ChangeData> create(Schema<ChangeData> schema,
      Collection<Account.Id> ids) throws QueryParseException {
    if (getField(schema) == LEGACY_REVIEWED) {
      throw new QueryParseException("Only is:reviewed is supported");
    }
    List<Predicate<ChangeData>> predicates = new ArrayList<>(ids.size());
    for (Account.Id id : ids) {
      predicates.add(new IsReviewedPredicate(id));
    }
    return Predicate.or(predicates);
  }

  @SuppressWarnings("deprecation")
  private static FieldDef<ChangeData, ?> getField(Schema<ChangeData> schema) {
    Optional<FieldDef<ChangeData, ?>> f =
        schema.getField(REVIEWEDBY, LEGACY_REVIEWED);
    checkState(f.isPresent(), "Schema %s missing field %s",
        schema.getVersion(), REVIEWEDBY.getName());
    return f.get();
  }

  private final Account.Id id;

  private IsReviewedPredicate(Account.Id id) {
    super(REVIEWEDBY, Integer.toString(id.get()));
    this.id = id;
  }

  @Override
  public boolean match(ChangeData cd) throws OrmException {
    Set<Account.Id> reviewedBy = cd.reviewedBy();
    return !reviewedBy.isEmpty() ? reviewedBy.contains(id) : id == NOT_REVIEWED;
  }

  @Override
  public int getCost() {
    return 1;
  }
}
