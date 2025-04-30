package com.xhield.parser.mavenpomparser.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DependencyDetail {
    private String groupId;
    private String artifactId;
    private String version;
    private String parent;     // Maven GAV string of the parent dependency
    private String scope;      // compile, test, runtime, provided, system
    private boolean optional;  // If the dependency is marked optional
    private int depth;         // Depth from the root dependency (0 = direct)
}
