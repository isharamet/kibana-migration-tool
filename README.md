# kibana-migration-tool

Simple library for performing Kibana saved objects migration between different instances.

## Building

```bash
$ lein uberjar
```

## Usage

Export:

```bash
$ java -jar kmt.jar export --source "http://localhost:9200" --destination "/path/to/kibana"
```

Import:

```bash
$ java -jar kmt.jar import --source "/path/to/kibana" --destination "http://localhost:9200"
```

## TODO

- [ ] Export specific objects with their dependencies
- [ ] Add support for basic authentication

