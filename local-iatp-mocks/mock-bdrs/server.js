const express = require("express");
const zlib = require("zlib");

const app = express();

app.get("/bpn-directory", (req, res) => {
  const payload = {
    "BPNL000000000000": "did:web:example.com",
    "BPNL000000000002": "did:web:localhost%3A19443:other-connector",
    "BPNL1234567890ZZ": "did:web:localhost%3A19443:dev-connector"
  };

  const json = JSON.stringify(payload);

  // Always gzip, because the Java client always assumes gzip
  const gz = zlib.gzipSync(Buffer.from(json, "utf8"));

  res.setHeader("Content-Type", "application/json");
  res.setHeader("Content-Encoding", "gzip");
  res.status(200).send(gz);
});

app.get("/health", (req, res) => res.json({ ok: true }));

app.listen(13000, () => console.log("Mock BDRS listening on :13000"));
