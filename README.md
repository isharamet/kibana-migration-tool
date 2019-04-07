# kibana-migration-tool

Simple library for performing Kibana saved objects migration between different instances.


## Usage

Export:

`kmt export --source "http://localhost:9200" --destination "/path/to/kibana"`

Import:

`kmt import --source "/path/to/kibana" --destination "http://localhost:9200"`

## TODO

- [ ] Export specific objects with their dependencies
- [ ] Add support for basic authentication

