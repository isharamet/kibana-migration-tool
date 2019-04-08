# kibana-migration-tool

Simple library for performing Kibana saved objects migration between different instances.

## Rationale

Even with recently-introduced saved object import/export functionality in Kibana currently there is no way to export import only subset of objects with all of their dependencies (please, see [issue 2730](https://github.com/elastic/kibana/issues/27306) ). So, there is a need (at least for me) for simple tool which will help doing such migrations. Also it will help with change tracking and object versioning since it exports each object into separate file.

## Building

```bash
$ lein uberjar
```

## Usage

Export:

```bash
$ java -jar kmt.jar export --source "http://localhost:9200" --destination "/path/to/kibana" --objects "dashboard:123456,search:23456"
```

Import:

```bash
$ java -jar kmt.jar import --source "/path/to/kibana" --destination "http://localhost:9200" --objects "dashboard:123456,search:23456"
```

## TODO

- [ ] Export specific objects with their dependencies
- [ ] Add support for basic authentication

