export default {
  async fetch(request, env) {
    const cors = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Authorization, Content-Type",
      "Access-Control-Max-Age": "86400",
      "Cache-Control": "no-store"
    };
    const json = (data, status = 200) => Response.json(data, { status, headers: cors });
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
    const path = new URL(request.url).pathname;
    const requestId = crypto.randomUUID();
    const log = (level, event, details = {}) => console[level](JSON.stringify({service:"jarvis-speech-api",requestId,path,event,...details}));
    log("info","request_started",{method:request.method});
    if (request.method === "GET" && (path === "/" || path === "/health")) {
      return json({service:"JARVIS AI API",status:"online",speech:"available",geminiConfiguredKeys:keys(env).length,textModel:"gemini-2.5-flash",speechModel:"gemini-2.5-flash-preview-tts",voicePipeline:"Gemini Live native bidirectional audio",liveModel:"gemini-3.8-live"});
    }
    if (!((request.method === "GET" && path === "/live") || (request.method === "POST" && ["/transcribe","/chat","/vision","/tts"].includes(path)))) return json({detail:"Not found."},404);
    try {
      const authorization = request.headers.get("Authorization");
      if (!authorization?.startsWith("Bearer ") || !env.SUPABASE_URL || !env.SUPABASE_ANON_KEY) { log("warn","authentication_configuration_missing"); return json({detail:"Authentication required.",requestId},401); }
      const auth = await fetch(String(env.SUPABASE_URL).replace(/\/+$/,"") + "/auth/v1/user", {headers:{apikey:env.SUPABASE_ANON_KEY,Authorization:authorization}});
      if (!auth.ok) { log("warn","supabase_auth_rejected",{status:auth.status}); return json({detail:"Your session is invalid. Please sign in again.",requestId},401); }
      const user = await auth.json();

      if (path === "/live") return await handleLive(request,env,user,log,json);

      if (path === "/transcribe") {
        const form = await request.formData();
        const audio = form.get("audio");
        if (!audio || typeof audio === "string" || typeof audio.arrayBuffer !== "function") return json({detail:"Audio file is missing."},400);
        if (audio.size <= 0) return json({detail:"Audio recording is empty."},400);
        if (audio.size > 10*1024*1024) return json({detail:"Recording is too large."},413);
        const bytes = new Uint8Array(await audio.arrayBuffer());
        let binary = "";
        for (let i=0;i<bytes.length;i+=0x8000) binary += String.fromCharCode(...bytes.subarray(i,i+0x8000));
        const result = await env.AI.run("@cf/openai/whisper-large-v3-turbo",{audio:btoa(binary),task:"transcribe"});
        const text = String(result?.text || "").trim();
        if (!text) return json({detail:"No speech was detected. Please try again."},422);
        return json({text,word_count:result?.word_count || text.split(" ").filter(Boolean).length});
      }

      if (!keys(env).length) { log("error","gemini_keys_missing"); return json({detail:"Gemini is not configured yet.",requestId},503); }
      const body = await readBody(request,path === "/chat" ? 262144 : path === "/vision" ? 3*1024*1024 : path === "/tts" ? 65536 : 16384);
      if (!body) return json({detail:"Invalid JSON request."},400);
      const language = label(body.language,48) || "the language used by the learner";
      const tutor = label(body.tutorName,64) || "JARVIS";


      if (path === "/vision") {
        const imageBase64 = typeof body.imageBase64 === "string" ? body.imageBase64 : "";
        if (!imageBase64 || imageBase64.length > 2*1024*1024 || !/^[A-Za-z0-9+/]+={0,2}$/.test(imageBase64)) return json({detail:"A valid JPEG frame is required."},400);
        const prompt = label(body.prompt,500) || "Describe the important visible details for a live tutoring conversation.";
        const payload = {
          contents:[{role:"user",parts:[
            {text:prompt},
            {inlineData:{mimeType:"image/jpeg",data:imageBase64}}
          ]}],
          generationConfig:{temperature:0.3,maxOutputTokens:500}
        };
        log("info","vision_frame_analysis_started",{imageCharacters:imageBase64.length});
        const result = await gemini(env,key => fetch("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",{method:"POST",headers:{"Content-Type":"application/json","x-goog-api-key":key},body:JSON.stringify(payload)}),{requestId,path:"/vision"});
        if (!result.ok) { log("error","vision_provider_failed",{httpStatus:result.status,providerStatus:result.providerStatus || null}); return json({detail:"JARVIS could not analyse this video frame. Please try again."},502); }
        const data = await result.response.json();
        const description = (data?.candidates?.[0]?.content?.parts || []).map(p => typeof p.text === "string" ? p.text : "").join("").trim();
        if (!description) return json({detail:"JARVIS could not identify useful details in this frame."},502);
        log("info","vision_frame_analysis_completed",{responseCharacters:description.length});
        return json({description,model:"gemini-2.5-flash",requestId});
      }

      if (path === "/tts") {
        const text = typeof body.text === "string" ? body.text.trim() : "";
        if (!text || text.length > 10000) return json({detail:"Speech text must contain 1 to 10,000 characters."},400);
        const requestedVoice = label(body.voice,32) || "Charon";
        const voices = new Set(["Achernar","Achird","Algenib","Algieba","Alnilam","Aoede","Autonoe","Callirrhoe","Charon","Despina","Enceladus","Erinome","Fenrir","Gacrux","Iapetus","Kore","Laomedeia","Leda","Orus","Pulcherrima","Puck","Rasalgethi","Sadachbia","Sadaltager","Schedar","Sulafat","Umbriel","Vindemiatrix","Zephyr","Zubenelgenubi"]);
        const voice = voices.has(requestedVoice) ? requestedVoice : "Charon";
        const speechText = "Speak in a warm, natural, conversational style with clear pronunciation and relaxed pacing. Read the following response faithfully; do not add or omit information: " + text;
        const payload = {
          contents:[{role:"user",parts:[{text:speechText}]}],
          generationConfig:{
            responseModalities:["AUDIO"],
            speechConfig:{voiceConfig:{prebuiltVoiceConfig:{voiceName:voice}}}
          }
        };
        log("info","tts_generation_started",{model:"gemini-2.5-flash-preview-tts",voice,textCharacters:text.length});
        const result = await gemini(env,key => fetch("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-preview-tts:generateContent",{method:"POST",headers:{"Content-Type":"application/json","x-goog-api-key":key},body:JSON.stringify(payload)}),{requestId,path:"/tts"});
        if (!result.ok) {
          log("error","tts_provider_failed",{httpStatus:result.status,providerStatus:result.providerStatus || null,providerMessage:result.providerMessage || null});
          return json({detail:"JARVIS could not generate speech right now. Please try again shortly.",requestId},502);
        }
        const data = await result.response.json();
        const audioPart = (data?.candidates?.[0]?.content?.parts || []).find(part => part?.inlineData?.data || part?.inline_data?.data);
        const audio = audioPart?.inlineData?.data || audioPart?.inline_data?.data || "";
        const mimeType = audioPart?.inlineData?.mimeType || audioPart?.inline_data?.mime_type || "audio/pcm;rate=24000";
        if (!audio) {
          log("error","tts_response_missing_audio",{candidateCount:data?.candidates?.length || 0});
          return json({detail:"The speech service returned no audio. Please try again.",requestId},502);
        }
        log("info","tts_generation_completed",{audioCharacters:audio.length,mimeType,voice});
        return json({audioBase64:audio,mimeType,voice,model:"gemini-2.5-flash-preview-tts",requestId});
      }

      if (path === "/chat") {
        if (!Array.isArray(body.messages) || body.messages.length < 1 || body.messages.length > 40) return json({detail:"A valid conversation message list is required."},400);
        const contents = [];
        for (const item of body.messages) {
          if (!item || !["user","model"].includes(item.role) || typeof item.content !== "string") return json({detail:"One or more conversation messages are invalid."},400);
          const content = item.content.trim();
          if (!content || content.length > 30000) return json({detail:"A conversation message is empty or too long."},400);
          contents.push({role:item.role,parts:[{text:content}]});
        }
        if (contents.at(-1).role !== "user") return json({detail:"The latest message must be from the user."},400);
        const systemInstruction = [
          "You are JARVIS AI, a patient and accurate personal tutor for Flexi Digital Academy.",
          "Explain clearly, adapt to the learner, use examples, and show understandable steps for academic problems.",
          "Never claim to have searched, accessed files, or performed actions unless a tool result confirms it.",
          "Respond in " + language + " unless the learner requests another language.",
          "Your tutor display name is " + tutor + "."
        ].join(" ");
        const payload = {systemInstruction:{parts:[{text:systemInstruction}]},contents,generationConfig:{temperature:0.7,maxOutputTokens:2048}};
        if (body.webSearch === true) payload.tools = [{google_search:{}}];
        const result = await gemini(env,key => fetch("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",{method:"POST",headers:{"Content-Type":"application/json","x-goog-api-key":key},body:JSON.stringify(payload)}));
        if (!result.ok) return json({detail:"JARVIS could not get an AI response right now. Please try again shortly."},502);
        const data = await result.response.json();
        const reply = (data?.candidates?.[0]?.content?.parts || []).map(p => typeof p.text === "string" ? p.text : "").join("").trim();
        if (!reply) return json({detail:"JARVIS did not return a text response. Please try again."},502);
        return json({reply,model:"gemini-2.5-flash",usage:data?.usageMetadata || null});
      }

      return json({detail:"Not found."},404);

    } catch (error) {
      log("error","unhandled_worker_exception",{name:error?.name || "Error",message:String(error?.message || "unknown").slice(0,1200),stack:String(error?.stack || "").slice(0,1800)});
      return json({detail:"JARVIS encountered a server error. Please try again.",requestId},500);
    }
  }
};
async function readBody(request,max) {
  const length = Number(request.headers.get("Content-Length") || 0);
  if (length > max) return null;
  const raw = await request.text();
  if (new TextEncoder().encode(raw).byteLength > max) return null;
  try { return JSON.parse(raw); } catch { return null; }
}
function label(value,max) {
  return typeof value === "string" ? value.replace(/[\u0000-\u001f\u007f]/g," ").trim().slice(0,max) : "";
}
function keys(env) {
  return [env.GEMINI_API_KEY_1,env.GEMINI_API_KEY_2,env.GEMINI_API_KEY_3,env.GEMINI_API_KEY_4,env.GEMINI_API_KEY_5].filter(v => typeof v === "string" && v.trim());
}
async function gemini(env,make,context={}) {
  const list = keys(env);
  let last = 503;
  let providerStatus = null;
  let providerMessage = null;
  let attempts = 0;
  for (let i=0;i<list.length;i++) {
    attempts++;
    let response;
    try {
      response = await make(list[i]);
    } catch (error) {
      last = 502;
      providerStatus = "NETWORK_ERROR";
      providerMessage = String(error?.message || "Fetch failed").slice(0,1000);
      console.error(JSON.stringify({service:"jarvis-speech-api",event:"gemini_fetch_exception",requestId:context.requestId,path:context.path,keySlot:i+1,attempt:attempts,message:providerMessage}));
      continue;
    }
    if (response.ok) {
      console.info(JSON.stringify({service:"jarvis-speech-api",event:"gemini_request_succeeded",requestId:context.requestId,path:context.path,keySlot:i+1,httpStatus:response.status,attempt:attempts}));
      return {ok:true,response,status:response.status,attempts};
    }
    last = response.status;
    providerStatus = null;
    providerMessage = "Provider returned HTTP " + response.status;
    let data = {};
    try { data = await response.clone().json(); } catch {}
    const rawMessage = String(data?.error?.message || data?.message || "");
    providerStatus = String(data?.error?.status || data?.error?.code || "");
    providerMessage = rawMessage.slice(0,1200) || providerMessage;
    console.error(JSON.stringify({service:"jarvis-speech-api",event:"gemini_request_rejected",requestId:context.requestId,path:context.path,keySlot:i+1,httpStatus:last,providerStatus,providerMessage,attempt:attempts}));
    const message = rawMessage.toLowerCase();
    const status = providerStatus.toUpperCase();
    const invalidKey = response.status===400 && (message.includes("api key not valid") || message.includes("invalid api key") || message.includes("api_key_invalid"));
    const quota = response.status===403 && (status==="RESOURCE_EXHAUSTED" || message.includes("quota") || message.includes("rate limit"));
    if (![429,500,502,503,504].includes(response.status) && !invalidKey && !quota) return {ok:false,status:last,providerStatus,providerMessage,attempts};
    console.warn(JSON.stringify({service:"jarvis-speech-api",event:"gemini_key_failover",requestId:context.requestId,path:context.path,failedKeySlot:i+1,httpStatus:last,attempt:attempts}));
  }
  return {ok:false,status:last,providerStatus,providerMessage,attempts};
}

async function handleLive(request,env,user,log,json) {
  if (request.headers.get("Upgrade")?.toLowerCase() !== "websocket") return json({detail:"A WebSocket upgrade is required for Live calls."},426);
  const apiKeys = keys(env);
  if (!apiKeys.length) return json({detail:"Gemini is not configured on the Cloudflare service."},503);
  const tutor = label(request.headers.get("X-Jarvis-Tutor"),64) || "JARVIS";
  const language = label(request.headers.get("X-Jarvis-Language"),48) || "English";
  const requestId = crypto.randomUUID();
  const instruction = [
    "You are " + tutor + ", JARVIS AI Tutor for Flexi Digital Academy.",
    "This is a natural, real-time voice and multimodal tutoring conversation. Speak warmly and conversationally, keep most answers concise, and allow the learner to interrupt.",
    "Use the learner's camera or shared-screen frames when provided. Explain visible diagrams, documents and readable text. Teach accurately and ask brief clarifying questions when needed.",
    "Speak in " + language + " unless the learner asks for another language."
  ].join(" ");
  let upstream = null;
  let lastFailure = "Gemini Live did not accept the connection.";
  for (let index=0; index<apiKeys.length; index++) {
    const url = "https://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=" + encodeURIComponent(apiKeys[index]);
    try {
      const response = await fetch(url,{headers:{Upgrade:"websocket"}});
      if (response.status===101 && response.webSocket) {
        upstream=response.webSocket;
        log("info","live_upstream_connected",{requestId,userId:user?.id||null,keySlot:index+1,model:"gemini-3.8-live",apiVersion:"v1beta"});
        break;
      }
      let detail="";
      try { detail=(await response.text()).slice(0,500); } catch {}
      lastFailure="Provider handshake HTTP "+response.status+(detail?": "+detail:"");
      log("warn","live_upstream_handshake_rejected",{requestId,keySlot:index+1,httpStatus:response.status,detail});
    } catch(error) {
      lastFailure=redactSecrets(String(error?.message||"Provider connection failed")).slice(0,500);
      log("warn","live_upstream_connect_error",{requestId,keySlot:index+1,message:lastFailure});
    }
  }
  if(!upstream) {
    log("error","live_upstream_unavailable",{requestId,reason:redactSecrets(lastFailure)});
    return json({detail:"Cloudflare could not establish the Gemini Live connection. "+redactSecrets(lastFailure),requestId},502);
  }
  const pair=new WebSocketPair();
  const client=pair[0], server=pair[1];
  server.accept();
  upstream.accept();
  let ready=false, ended=false;
  const setupTimer=setTimeout(()=>{
    if(!ready&&!ended){
      sendClient({type:"error",message:"Cloudflare could not complete the live session setup within 20 seconds."});
      closeBoth(1011,"Live setup timeout");
    }
  },20000);
  const sendClient=(payload)=>{
    if(ended||server.readyState!==WebSocket.OPEN) return;
    try { server.send(JSON.stringify(payload)); } catch {}
  };
  const closeBoth=(code=1000,reason="Session ended")=>{
    if(ended) return;
    ended=true;
    clearTimeout(setupTimer);
    try { upstream.close(code,reason.slice(0,120)); } catch {}
    try { server.close(code,reason.slice(0,120)); } catch {}
  };
  upstream.addEventListener("message",async event=>{
    try {
      let raw = "";
      if (typeof event.data === "string") raw = event.data;
      else if (event.data instanceof ArrayBuffer) raw = new TextDecoder().decode(event.data);
      else if (ArrayBuffer.isView(event.data)) raw = new TextDecoder().decode(event.data);
      else if (typeof Blob !== "undefined" && event.data instanceof Blob) raw = await event.data.text();
      if (!raw || !raw.trim()) {
        log("warn","live_provider_empty_frame",{requestId,dataType:typeof event.data});
        return;
      }
      const root=JSON.parse(raw);
      if(root.error) {
        const detail=root.error.message||root.error.status||"Gemini rejected the Live session.";
        log("error","live_provider_session_error",{requestId,status:root.error.status||null,message:String(detail).slice(0,800)});
        sendClient({type:"error",message:String(detail).slice(0,800)});
        closeBoth(1011,"Provider session rejected"); return;
      }
      if(root.setupComplete) {
        ready=true; clearTimeout(setupTimer); sendClient({type:"ready"});
        log("info","live_setup_complete",{requestId,model:"gemini-3.8-live"});
      }
      const content=root.serverContent||{};
      const inputText=content.inputTranscription?.text||root.inputTranscription?.text||"";
      if(inputText) sendClient({type:"transcript",role:"user",text:inputText});
      const outputText=content.outputTranscription?.text||root.outputTranscription?.text||"";
      if(outputText) sendClient({type:"transcript",role:"assistant",text:outputText});
      for(const part of (content.modelTurn?.parts||[])) {
        const inline=part.inlineData||part.inline_data;
        const audio=inline?.data||"";
        if(audio) sendClient({type:"audio",mimeType:inline.mimeType||inline.mime_type||"audio/pcm;rate=24000",data:audio});
        if(part.text&&!outputText) sendClient({type:"transcript",role:"assistant",text:part.text});
      }
      if(content.interrupted) sendClient({type:"interrupted"});
      if(content.turnComplete) sendClient({type:"turn_complete"});
      for(const call of (root.toolCall?.functionCalls||[])) {
        if(call.name==="end_call") {
          const reason=String(call.args?.reason||"The learner's session has ended.").slice(0,300);
          upstream.send(JSON.stringify({toolResponse:{functionResponses:[{id:call.id,name:"end_call",response:{status:"ending",reason}}]}}));
          sendClient({type:"end_call",reason});
        }
      }
    } catch(error) {
      log("error","live_provider_message_parse_error",{requestId,message:String(error?.message||error).slice(0,400)});
      sendClient({type:"error",message:"Cloudflare could not process a response from the live service."});
    }
  });
  upstream.addEventListener("close",event=>{
    log("info","live_upstream_closed",{requestId,code:event.code,reason:String(event.reason||"").slice(0,200),setupComplete:ready});
    if(!ended) {
      if(!ready) sendClient({type:"error",message:"The provider closed the session before setup completed (code "+event.code+")."});
      closeBoth(event.code>=1000&&event.code<5000&&![1005,1006,1015].includes(event.code)?event.code:1011,String(event.reason||"Provider connection closed").slice(0,120));
    }
  });
  upstream.addEventListener("error",()=>{
    log("error","live_upstream_socket_error",{requestId,setupComplete:ready});
    sendClient({type:"error",message:"The Cloudflare-to-provider live connection failed."});
    closeBoth(1011,"Provider connection error");
  });
  server.addEventListener("message",event=>{
    if(ended||typeof event.data!=="string") return;
    if(event.data.length>3*1024*1024) {
      sendClient({type:"error",message:"The media frame is too large."}); closeBoth(1009,"Message too large"); return;
    }
    try {
      const message=JSON.parse(event.data);
      if(message.type==="audio"&&typeof message.data==="string") {
        upstream.send(JSON.stringify({realtimeInput:{audio:{mimeType:"audio/pcm;rate=16000",data:message.data}}}));
      } else if(message.type==="video"&&typeof message.data==="string") {
        upstream.send(JSON.stringify({realtimeInput:{video:{mimeType:"image/jpeg",data:message.data}}}));
      } else if(message.type==="audio_end") {
        upstream.send(JSON.stringify({realtimeInput:{audioStreamEnd:true}}));
      } else if(message.type==="text"&&typeof message.text==="string"&&message.text.trim()) {
        upstream.send(JSON.stringify({clientContent:{turns:[{role:"user",parts:[{text:message.text.slice(0,12000)}]}],turnComplete:true}}));
      } else if(message.type==="end") closeBoth(1000,"Call ended");
      else sendClient({type:"error",message:"Unsupported Live input message."});
    } catch(error) {
      sendClient({type:"error",message:"Cloudflare could not process the incoming media frame."});
      log("warn","live_client_message_rejected",{requestId,message:String(error?.message||error).slice(0,300)});
    }
  });
  server.addEventListener("close",event=>{
    if(!ended) closeBoth(event.code>=1000&&event.code<5000&&![1005,1006,1015].includes(event.code)?event.code:1000,String(event.reason||"App disconnected").slice(0,120));
  });
  server.addEventListener("error",()=>closeBoth(1011,"App socket error"));
  upstream.send(JSON.stringify({setup:{
    model:"models/gemini-3.8-live",
    systemInstruction:{parts:[{text:instruction}]},
    generationConfig:{responseModalities:["AUDIO"],speechConfig:{voiceConfig:{prebuiltVoiceConfig:{voiceName:"Kore"}}}},
    inputAudioTranscription:{},
    outputAudioTranscription:{},
    tools:[{functionDeclarations:[{name:"end_call",description:"End the live tutoring conversation when the learner asks to stop.",parameters:{type:"OBJECT",properties:{reason:{type:"STRING"}}}}]}]
  }}));
  log("info","live_session_started",{requestId,userId:user?.id||null,model:"gemini-3.8-live",apiVersion:"v1beta"});
  return new Response(null,{status:101,webSocket:client});
}

function redactSecrets(value) {
  return String(value || "").replace(/([?&]key=)[^&\\s]+/gi, "$1[redacted]");
}
