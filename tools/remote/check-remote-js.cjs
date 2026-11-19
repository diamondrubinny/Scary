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
const { sha256, hmac, seal, openAnswer } = require(path.join(__dirname, "../../app/src/main/assets/remote/remote.js"));

const secret = Uint8Array.from({ length: 32 }, (_, i) => i);
const sealed = JSON.parse(seal(secret, 1700000000000123, "search", "תורשה hello"));
assert.strictEqual(sealed.x, "lCSMPrVe0q1eDOqSJpeOMw==");
assert.strictEqual(sealed.t, "67010163bd2667a595d692192b62c5920652e318ba8a6959f2fc6a394a75000e");
assert.strictEqual(JSON.parse(seal(secret, 5, "up", "")).t, "8a90b01f0bfc091424cb0905fdad00b29290b0b4218a66fabcac74f7a9772ffb");

// An answer that carries a backup (RemoteProtocol.sealAnswer), and a backup file sent to restore.
const backupText = '{"format":"scary-backup","version":1,"myList":["the-shining-1980","cm_tt0078748"]}';
const answerCipher = "RS0zrphbi1qii+4tHJdYUD9cszb2vV1nwOIUU0HnlmkHVy33nwf/0gzoUbnaOPQlo69VfHvAmwBmQJrsfYxavU6AW6XThV0n4UH446k3SXMOTA==";
const answerTag = "1238871ebae82d0b7634d66339faef4ea69b506c0cdc1939f351e2a8d6f8ea9f";
assert.strictEqual(openAnswer(secret, 1700000000000456, answerCipher, answerTag), backupText);
assert.strictEqual(openAnswer(secret, 1700000000000457, answerCipher, answerTag), null);
assert.strictEqual(openAnswer(secret, 1700000000000456, answerCipher, answerTag.replace(/^1/, "2")), null);
assert.strictEqual(openAnswer(secret, 1700000000000456, answerCipher, ""), null);
assert.strictEqual(openAnswer(secret, 1700000000000456, "not base64!", answerTag), null);
const bigBackup = '{"format":"scary-backup","version":1,"watched":[' + Array.from({ length: 600 }, (_, i) => '"f' + i + '"').join(",") + "]}";
assert.strictEqual(bigBackup.length, 4139);
const bigSealed = JSON.parse(seal(secret, 7, "restore", bigBackup));
assert.strictEqual(bigSealed.t, "d12138415c10394ed2fef2d3c5bec647bb3107db36a5f5b0ab625df3b70c6c4d");
assert.strictEqual(crypto.createHash("sha256").update(Buffer.from(bigSealed.x, "base64")).digest("hex"), "efd8b665c361bd144c56071406bda2f0ab01b1437c2e8bfb86c3601465e2bd86");
// The longest backup the TV takes is sealed without running out of stack.
assert.ok(seal(secret, 8, "restore", "x".repeat(120000)).length <= 170000);

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
