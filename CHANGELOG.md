# ebx-linkedin-sdk Changelog

## 1.0.0 (November 21, 2022)

* Initial release

## 2.0.0 (June 5, 2023)

* Project JAVA version upgraded to 17, due to new dependencies. This caused the major version bump.
* Added optional analyser classes to allow for additional PR enrichment. For example with JIRA data.
* After PR exports creates a cross reference lookup document named childissues_to_epic.csv which 
  can be used to determine work types for all tracked issues.