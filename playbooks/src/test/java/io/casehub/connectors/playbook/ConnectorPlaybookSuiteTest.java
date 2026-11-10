package io.casehub.connectors.playbook;

import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectDirectories;
import org.junit.platform.suite.api.Suite;

@Suite
@IncludeEngines("casehub-yaml")
@SelectDirectories("src/test/playbooks")
class ConnectorPlaybookSuiteTest {
}
