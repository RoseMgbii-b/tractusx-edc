const express = require("express");
const bodyParser = require("body-parser");
const fs = require("fs");
const jwt = require("jsonwebtoken");

const app = express();
app.use(bodyParser.json({ limit: "2mb" }));

// Read private key from mounted /keys volume
const PRIVATE_KEY_PEM = fs.readFileSync("/keys/did-key-private.pem", "utf8");

// Set this to match your connector DID (the DID the connector uses as issuer)
const ISSUER_DID = "did:web:localhost%3A19443:dev-connector";

app.post("/presentations/query", (req, res) => {
  const auth = req.header("authorization") || "";
  console.log("POST /presentations/query");
  console.log("Auth:", auth);
  console.log("Body:", JSON.stringify(req.body));

  const ctx =
      req.body["@context"] ??
      ["https://www.w3.org/ns/did/v1"];

  // Minimal VP claim
  const vpClaim = {
    "@context": ["https://www.w3.org/2018/credentials/v1"],
    type: ["VerifiablePresentation"],
    verifiableCredential: []
  };

  const now = Math.floor(Date.now() / 1000);

  // This produces a 3-part JWS token (header.payload.signature)
  const signedJwt = jwt.sign(
      {
        iss: ISSUER_DID,
        sub: ISSUER_DID,
        aud: ISSUER_DID,
        iat: now,
        exp: now + 3600,
        vp: vpClaim
      },
      PRIVATE_KEY_PEM,
      {
        algorithm: "RS256",
        header: {
          typ: "JWT"
        }
      }
  );

  res.status(200).json({
    "@context": ctx,
    type: "PresentationResponseMessage",
    presentation: [signedJwt]
  });
});

app.get("/health", (req, res) => res.json({ ok: true }));
app.listen(14000, () => console.log("Mock Credential Service listening on :14000"));
