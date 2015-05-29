# JSON Schema Reference Inliner

Takes a "$ref": "anotherSchema.schema.json", dereferences it, and inserts the content of that schema. Works recursively.

## Usage

You need sbt to run this project.

Print inlined json schema to console:
sbt "run https://raw.githubusercontent.com/sphereio/sphere-api-reference/master/schemas/customers.schema.json"
Save inlined json into a file:
sbt "run https://raw.githubusercontent.com/sphereio/sphere-api-reference/master/schemas/customers.schema.json customers-inlined.schema.json"
You may use relative paths and '.' to use the original filename:
sbt "run https://raw.githubusercontent.com/sphereio/sphere-api-reference/master/schemas/customers.schema.json ../another/dir/."
You can also use a list of schema/file pairs

Note: No cyclic references yet.

This is production-quality prototype code :-)
