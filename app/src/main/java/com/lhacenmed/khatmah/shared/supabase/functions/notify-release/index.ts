import { createClient } from "npm:@supabase/supabase-js@2";
import { JWT } from "npm:google-auth-library@9";

// Types

interface ReleasePayload {
  versionName: string;
  versionCode: number;
  notes: string;
  apkUrl: string;
}

interface PushToken {
  token: string;
  device_id: string;
}

interface FcmResult {
  token: string;
  success: boolean;
  error?: string;
}

// Config

const serviceAccountRaw = Deno.env.get("FCM_SERVICE_ACCOUNT");
if (!serviceAccountRaw)
  throw new Error("FCM_SERVICE_ACCOUNT secret is not set");
const serviceAccount = JSON.parse(serviceAccountRaw);

const FCM_URL = `https://fcm.googleapis.com/v1/projects/${serviceAccount.project_id}/messages:send`;

// FCM auth

function getFcmAccessToken(): Promise<string> {
  return new Promise((resolve, reject) => {
    const jwt = new JWT({
      email: serviceAccount.client_email,
      key: serviceAccount.private_key,
      scopes: ["https://www.googleapis.com/auth/firebase.messaging"],
    });
    jwt.authorize((err, tokens) => {
      if (err || !tokens?.access_token)
        return reject(err ?? new Error("No access token"));
      resolve(tokens.access_token);
    });
  });
}

// Send one FCM data-only message

async function sendOne(
  token: string,
  payload: ReleasePayload,
  accessToken: string,
): Promise<FcmResult> {
  const body = JSON.stringify({
    message: {
      token,
      // data-only so KhatmahFcmService.onMessageReceived fires in every app state
      data: {
        type: "app_update",
        versionName: payload.versionName,
        versionCode: String(payload.versionCode),
        notes: payload.notes,
        apkUrl: payload.apkUrl,
      },
      android: {
        priority: "high",
      },
    },
  });

  const res = await fetch(FCM_URL, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${accessToken}`,
    },
    body,
  });

  if (res.ok) return { token, success: true };

  const err = await res
    .json()
    .catch(() => ({ error: { message: res.statusText } }));
  // UNREGISTERED / INVALID_REGISTRATION -> stale token; caller will prune it
  return { token, success: false, error: err?.error?.status ?? res.statusText };
}

// Handler

Deno.serve(async (req) => {
  // Verify the caller is our GitHub Actions workflow
  const secret = Deno.env.get("RELEASE_WEBHOOK_SECRET");
  if (req.headers.get("x-release-secret") !== secret) {
    return new Response("Unauthorized", { status: 401 });
  }

  if (req.method !== "POST") {
    return new Response("Method Not Allowed", { status: 405 });
  }

  let payload: ReleasePayload;
  try {
    payload = await req.json();
    if (!payload.versionName || !payload.apkUrl)
      throw new Error("Missing fields");
  } catch (e) {
    return new Response(JSON.stringify({ error: String(e) }), { status: 400 });
  }

  // Service-role client to read tokens and prune stale ones
  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );

  const { data: rows, error: dbErr } = await supabase
    .from("push_tokens")
    .select("token, device_id");

  if (dbErr) {
    return new Response(JSON.stringify({ error: dbErr.message }), {
      status: 500,
    });
  }

  const tokens = (rows ?? []) as PushToken[];
  if (tokens.length === 0) {
    return new Response(JSON.stringify({ sent: 0, pruned: 0 }));
  }

  const accessToken = await getFcmAccessToken();

  // Fan-out in parallel; FCM HTTP v1 has no batch endpoint for Android
  const results = await Promise.all(
    tokens.map((t) => sendOne(t.token, payload, accessToken)),
  );

  // Prune tokens FCM flagged as stale
  const stale = results
    .filter(
      (r) =>
        !r.success &&
        (r.error === "UNREGISTERED" || r.error === "INVALID_REGISTRATION"),
    )
    .map((r) => r.token);

  if (stale.length > 0) {
    await supabase.from("push_tokens").delete().in("token", stale);
  }

  const sent = results.filter((r) => r.success).length;
  console.log(
    `notify-release: sent=${sent} pruned=${stale.length} total=${tokens.length}`,
  );

  return new Response(
    JSON.stringify({ sent, pruned: stale.length, total: tokens.length }),
    { headers: { "Content-Type": "application/json" } },
  );
});
