# ebx-github-cycletime

## What it is

ebx-github-cycletime is a simple tool for exporting merged PRs from across a github organisation. 
This exported data can then be easily used in cycle time calculations, in addition to other 
analysis.

It is created and maintained by [Echobox](http://echobox.com).

## Licensing

ebx-github-cycletime itself is open source software released under the terms of the Apache 2.0 License.

## Getting Started

At the time of writing the application is intended to be run from source (>=Java 14). Use the 
following steps to get started:

1. Download the source.
2. Set an environment variable `ORG_ID=[org]` where _[org]_ is the github organisation you want 
   to analyse.   
3. Set another environment variable `GITHUB_OAUTH=[token]` where _[token]_ is a github token with 
   read access to the required organisation. Please see [here](https://github-api.kohsuke.org/index.html) 
   for alternative auth mechanisms if needed.
4. Run the application.

This will export all recent PR data (see `DEFAULT_EXPORT_DAYS_IF_NO_APPEND`) from the organisation 
into `RAW_CSV_FILENAME`. Subsequent steps will then clean this data by applying optional 
'preferred author name' mappings (i.e. swap github exported names for those provided in 
`preferred_author_names.csv`) and sort by the PR merge date. The cycle time aggregation uses 
`AUTHOR_NAMES_TO_SQUADS_CSV` to map authors to squads. Authors can be in multiple squads.

If the application is run again with the same configuration it will append new PRs to 
`RAW_CSV_FILENAME` and update all downstream documents using all available data.

For example config files please see `preferred_author_names.example.csv` and 
`author_names_to_squads.example.csv`. Just add your data as required and then remove the `.example` 
part of the filename.

**Alternatively you can modify Main.java to export just a single PR:**

```
GitHub github = GitHubBuilder.fromEnvironment().build();

GHOrganization githubOrg = github.getOrganization(System.getenv("ORG_ID"));

// Specify your own repo name here
String repoName = "ebx-linkedin-sdk";
GHRepository repo = githubOrg.getRepository(repoName);
int PrNum = 218;
GHPullRequest pullRequest = repo.getPullRequest(prNum);
PRAnalyser analyser = new PRAnalyser(repoName, new PullRequestKohsuke(pullRequest));
analyser.analyse();

// Then define what you want to do with the analysis
// ...
```

### Helpful notes:

#### Calculating PR reviewed by

Duplicate reviews within a short time period are only counted once.

#### Cycle time component values

A maximum component value is defined in PRAnalyser to avoid any anomalies due to force pushes or 
bad merge history.

#### How to manually verify

The best options (we're aware of) to  manually verify the exported data:

PRs Merged - https://github.com/pulls?q=is%3Apr+author%3AMarcF+merged%3A2022-03-01..2022-03-31

PRs Reviewed - https://github.com/search?q=is%3Apr+reviewed-by%3AMarcF+-author%3AMarcF+merged%3A2022-03-01..2022-03-31

## Contributing

If you would like to get involved please follow the instructions
[here](https://github.com/ebx/ebx-github-cycletime/tree/master/CONTRIBUTING.md)

## Releases

We use [semantic versioning](https://semver.org/).