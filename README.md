# ebx-github-cycletime

## What it is

ebx-github-cycletime is a simple tool for exporting merged PRs from across a github organisation. 
This exported data can then be easily used in cycle time calculations, in addition to other 
analysis.

It is created and maintained by [Echobox](http://echobox.com).

## Licensing

ebx-github-cycletime itself is open source software released under the terms of the Apache 2.0 License.

## Getting Started

At the time of writing this application is intended to be run from source. Use the following steps 
to get started:

1. Download the source.
2. Set an environment variable `GITHUB_OAUTH=[token]` where _[token]_ is a github token with 
   read access to the required organisation. Please see [here](https://github-api.kohsuke.org/index.html) for alternative auth mechanisms.
3. Open the Main.java source file needed for subsequent steps.   
4. Set the desired _orgIdentifier_ in source.
5. Set the desired _considerOnlyPRsMergedAfterUnixTime_ and 
   _considerOnlyPRsMergedBeforeUnixTime_ parameters in source.
6. Run the application.

This will export all PR data from the organisation as configured.

**Alternatively you can export just a single PR:**

7. Comment out the _analyseEntireOrg(..._ method and replace it with the _analyseSpecificPR(..._ 
   method.
8. Run the application again just for a single PR.   

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