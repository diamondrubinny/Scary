// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.
//
// Checks that the phone remote's page (app/src/main/assets/remote/remote.js) signs and encrypts exactly like the TV
// (core RemoteProtocol.kt): the expected values below were computed with Python's hmac/hashlib, and
// RemoteProtocolTest checks the Kotlin side against the same. Run: node tools/remote/check-remote-js.cjs
"use strict";
const assert = require("assert");
const crypto = require("crypto");
const path = require("path");
const { sha256, hmac, seal } = require(path.join(__dirname, "../../app/src/main/assets/remote/remote.js"));

const secret = Uint8Array.from({ length: 32 }, (_, i) => i);
const sealed = JSON.parse(seal(secret, 1700000000000123, "search", "תורשה hello"));
assert.strictEqual(sealed.x, "lCSMPrVe0q1eDOqSJpeOMw==");
assert.strictEqual(sealed.t, "67010163bd2667a595d692192b62c5920652e318ba8a6959f2fc6a394a75000e");
assert.strictEqual(JSON.parse(seal(secret, 5, "up", "")).t, "8a90b01f0bfc091424cb0905fdad00b29290b0b4218a66fabcac74f7a9772ffb");

// SHA-256 and HMAC against Node's own, on lengths around the block edges.
for (const length of [0, 1, 55, 56, 63, 64, 65, 119, 120, 1000]) {
  const data = crypto.randomBytes(length);
  assert.strictEqual(Buffer.from(sha256(new Uint8Array(data))).toString("hex"), crypto.createHash("sha256").update(data).digest("hex"));
  const key = crypto.randomBytes(length % 2 ? 32 : 80);
  assert.strictEqual(
    Buffer.from(hmac(new Uint8Array(key), new Uint8Array(data))).toString("hex"),
    crypto.createHmac("sha256", key).update(data).digest("hex"),
  );
}
console.log("remote.js agrees with RemoteProtocol.kt and Node's crypto.");
