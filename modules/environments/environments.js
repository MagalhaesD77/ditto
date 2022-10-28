/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

/* eslint-disable arrow-parens */
/* eslint-disable prefer-const */
/* eslint-disable require-jsdoc */
import * as Authorization from '../environments/authorization.js';
import * as Utils from '../utils.js';

const URL_PRIMARY_ENVIRONMENT_NAME = 'primaryEnvironmentName';
const URL_ENVIRONMENTS = 'environmentsURL';
const STORAGE_KEY = 'ditto-ui-env';

let urlSearchParams;
let environments;
let selectedEnvName;

let settingsEditor;

let dom = {
  environmentSelector: null,
  buttonCreateEnvironment: null,
  buttonDeleteEnvironment: null,
  buttonUpdateFields: null,
  buttonUpdateJson: null,
  inputEnvironmentName: null,
  inputApiUri: null,
  inputSearchNamespaces: null,
  selectDittoVersion: null,
  tbodyEnvironments: null,
  tableValidationEnvironments: null,
};

let observers = [];

function Environment(env) {
  Object.assign(this, env);
  Object.defineProperties(this, {
    bearer: {
      enumerable: false,
      writable: true,
    },
    usernamePassword: {
      value: this.defaultUsernamePassword,
      enumerable: false,
      writable: true,
    },
    usernamePasswordDevOps: {
      value: this.defaultUsernamePasswordDevOps,
      enumerable: false,
      writable: true,
    },
    dittoPreAuthenticatedUsername: {
      value: this.defaultDittoPreAuthenticatedUsername,
      enumerable: false,
      writable: true,
    },
  });
}

export function current() {
  return environments[dom.environmentSelector.value];
}

export function addChangeListener(observer) {
  observers.push(observer);
}

function notifyAll(modifiedField) {
  // Notify Authorization first to set right auth header
  Authorization.onEnvironmentChanged(modifiedField);
  // Notify others
  observers.forEach(observer => observer.call(null, modifiedField));
}

export async function ready() {
  Utils.getAllElementsById(dom);

  Utils.addValidatorToTable(dom.tbodyEnvironments, dom.tableValidationEnvironments);

  urlSearchParams = new URLSearchParams(window.location.search);
  environments = await loadEnvironmentTemplates();

  settingsEditor = ace.edit('settingsEditor');
  settingsEditor.session.setMode('ace/mode/json');

  dom.buttonUpdateJson.onclick = () => {
    Utils.assert(selectedEnvName, 'No environment selected', dom.tableValidationEnvironments);
    environments[selectedEnvName] = JSON.parse(settingsEditor.getValue());
    environmentsJsonChanged();
  };

  dom.buttonUpdateFields.onclick = () => {
    Utils.assert(selectedEnvName, 'No environment selected', dom.tableValidationEnvironments);
    if (selectedEnvName !== dom.inputEnvironmentName.value) {
      environments[dom.inputEnvironmentName.value] = environments[selectedEnvName];
      delete environments[selectedEnvName];
      selectedEnvName = dom.inputEnvironmentName.value;
    }
    environments[selectedEnvName].api_uri = dom.inputApiUri.value;
    environments[selectedEnvName].searchNamespaces = dom.inputSearchNamespaces.value;
    environments[selectedEnvName].ditto_version = dom.selectDittoVersion.value;
    environmentsJsonChanged();
  };

  dom.tbodyEnvironments.addEventListener('click', (event) => {
    if (event.target && event.target.tagName === 'TD') {
      if (selectedEnvName && selectedEnvName === event.target.parentNode.id) {
        selectedEnvName = null;
      } else {
        selectedEnvName = event.target.parentNode.id;
      }
      updateEnvEditors();
    }
  });

  dom.buttonCreateEnvironment.onclick = () => {
    Utils.assert(dom.inputEnvironmentName.value, 'Please provide an environment name', dom.inputEnvironmentName);
    Utils.assert(!environments[dom.inputEnvironmentName.value], 'Name already used', dom.inputEnvironmentName);
    environments[dom.inputEnvironmentName.value] = new Environment({
      api_uri: dom.inputApiUri.value ? dom.inputApiUri.value : '',
      ditto_version: dom.selectDittoVersion.value ? dom.selectDittoVersion.value : '3',
    });
    selectedEnvName = dom.inputEnvironmentName.value;
    environmentsJsonChanged();
  };

  dom.buttonDeleteEnvironment.onclick = () => {
    Utils.assert(selectedEnvName, 'No environment selected', dom.tableValidationEnvironments);
    Utils.assert(Object.keys(environments).length >= 2, 'At least one environment is required',
        dom.tableValidationEnvironments);
    delete environments[selectedEnvName];
    selectedEnvName = null;
    environmentsJsonChanged();
  };

  // Ensure that ace editor to refresh when updated while hidden
  document.querySelector('a[data-bs-target="#tabEnvJson"]').addEventListener('shown.bs.tab', () => {
    settingsEditor.renderer.updateFull();
  });

  dom.environmentSelector.onchange = () => {
    urlSearchParams.set(URL_PRIMARY_ENVIRONMENT_NAME, dom.environmentSelector.value);
    window.history.replaceState({}, '', `${window.location.pathname}?${urlSearchParams}`);
    notifyAll();
  };

  environmentsJsonChanged();
}

export function environmentsJsonChanged(modifiedField) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(environments));

  updateEnvSelector();
  updateEnvEditors();
  updateEnvTable();

  notifyAll(modifiedField);

  function updateEnvSelector() {
    let activeEnvironment = dom.environmentSelector.value;
    if (!activeEnvironment) {
      activeEnvironment = urlSearchParams.get(URL_PRIMARY_ENVIRONMENT_NAME);
    }
    if (!activeEnvironment || !environments[activeEnvironment]) {
      activeEnvironment = Object.keys(environments)[0];
    }

    Utils.setOptions(dom.environmentSelector, Object.keys(environments));

    dom.environmentSelector.value = activeEnvironment;
  }

  function updateEnvTable() {
    dom.tbodyEnvironments.innerHTML = '';
    Object.keys(environments).forEach((key) => {
      Utils.addTableRow(dom.tbodyEnvironments, key, key === selectedEnvName);
    });
  }
}

function updateEnvEditors() {
  if (selectedEnvName) {
    const selectedEnvironment = environments[selectedEnvName];
    dom.inputEnvironmentName.value = selectedEnvName;
    settingsEditor.setValue(JSON.stringify(selectedEnvironment, null, 2), -1);
    dom.inputApiUri.value = selectedEnvironment.api_uri;
    dom.inputSearchNamespaces.value = selectedEnvironment.searchNamespaces ?? '';
    dom.selectDittoVersion.value = selectedEnvironment.ditto_version ? selectedEnvironment.ditto_version : '3';
  } else {
    dom.inputEnvironmentName.value = null;
    settingsEditor.setValue('');
    dom.inputApiUri.value = null;
    dom.inputSearchNamespaces.value = null;
    dom.selectDittoVersion.value = 3;
  }
}

async function loadEnvironmentTemplates() {
  let fromTemplates = await (await fetch('templates/environmentTemplates.json')).json();
  let fromURL;
  let fromLocalStorage;

  let environmentsURL = urlSearchParams.get(URL_ENVIRONMENTS);
  if (environmentsURL) {
    try {
      let response = await fetch(environmentsURL);
      if (!response.ok) {
        throw new Error(`URL ${environmentsURL} can not be loaded`);
      }
      fromURL = await response.json();
      validateEnvironments(fromURL);
    } catch (err) {
      fromURL = null;
      Utils.showError(
          err.message,
          'Error loading environments from URL');
    }
  }

  const restoredEnv = localStorage.getItem(STORAGE_KEY);
  if (restoredEnv) {
    fromLocalStorage = JSON.parse(restoredEnv);
  }

  let result;

  if (fromURL) {
    if (fromLocalStorage) {
      result = merge(fromURL, fromLocalStorage);
    } else {
      result = fromURL;
    }
  } else if (fromLocalStorage) {
    result = fromLocalStorage;
  } else {
    result = fromTemplates;
  }

  Object.keys(result).forEach((env) => {
    result[env] = new Environment(result[env]);
  });

  return result;

  function validateEnvironments(envs) {
    Object.keys(envs).forEach((key) => {
      if (!envs[key].hasOwnProperty('api_uri')) {
        throw new SyntaxError('Environments json invalid');
      }
    });
  };

  function merge(remote, local) {
    let merged = {};
    Object.keys(remote).forEach((env) => {
      merged[env] = {
        ...local[env],
        ...remote[env],
      };
    });
    return {
      ...local,
      ...merged,
    };
  }
}


