/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GerritView} from '../../services/router/router-model';
import {getBaseUrl} from '../../utils/url-util';
import {define} from '../dependency';
import {Model} from '../base/model';
import {ViewState} from './base';

export interface DocumentationViewState extends ViewState {
  view: GerritView.DOCUMENTATION_SEARCH;
  filter: string;
}

/**
 * This is just for documentation *searches*, not for static documentation
 * URLs. See `getDocUrl()` in url-util.ts.
 */
export function createDocumentationUrl() {
  return `${getBaseUrl()}/Documentation`;
}

export const documentationViewModelToken = define<DocumentationViewModel>(
  'documentation-view-model'
);

export class DocumentationViewModel extends Model<
  DocumentationViewState | undefined
> {
  constructor() {
    super(undefined);
  }
}
