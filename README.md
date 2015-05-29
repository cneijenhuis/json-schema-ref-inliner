# JSON Schema Reference Inliner

Takes a "$ref": "anotherSchema.schema.json", dereferences it, and inserts the content of that schema. Works recursively.

## Usage

sbt "run https://raw.githubusercontent.com/sphereio/sphere-api-reference/master/schemas/customers.schema.json"

Note: No cyclic references yet.

This is production-quality prototype code :-)
