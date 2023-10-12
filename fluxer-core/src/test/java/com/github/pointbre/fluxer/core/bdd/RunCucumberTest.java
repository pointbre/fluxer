package com.github.pointbre.fluxer.core.bdd;


import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.pointbre.fluxer.core.PortNumberExtension;
import com.github.pointbre.fluxer.core.Slf4jExtension;

// 1 test runner seems OK, but it's still possible to create multiple runners for parallel execution
@Suite
@IncludeEngines("cucumber")
// Where feature files are located
@SelectClasspathResource("com/github/pointbre/fluxer/core/bdd")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty")
// glue path: See https://www.programsbuzz.com/article/cucumber-options-glue
// sub directories are all included as cucumber flattens the directory structures
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.github.pointbre.fluxer.core.bdd")
public class RunCucumberTest {
}
