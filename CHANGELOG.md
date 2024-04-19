# ebx-linkedin-sdk Changelog

## 1.0.0 (November 21, 2022)

* Initial release

## 2.0.0 (June 5, 2023)

* Project JAVA version upgraded to 17, due to new dependencies. This caused the major version bump.
* Added optional analyser classes to allow for additional PR enrichment. For example with JIRA data.
* After PR exports creates a cross reference lookup document named childissues_to_epic.csv which 
  can be used to determine work types for all tracked issues.

## 2.0.1 (Jan 4, 2024)

* Update the JQL statements to select 2024 issues only

## 2.0.2 (April 18, 2024)

* `export_sorted_by_mergedate.csv` and `export_sorted_by_mergedate` will only include distinct 
  PR reviewers.
* `export_sorted_by_mergedate.csv` and `export_sorted_by_mergedate` will exclude revert PRs by
  excluding PRs with the word 'revert' in the title.
