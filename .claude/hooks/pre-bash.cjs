const realFs = require('fs');
let STDIN = '';
try {
  STDIN = realFs.readFileSync(0, 'utf-8');
} catch (err) {
  // no payload: every check allows
}
class HookExit { constructor(code) { this.code = code; } }
const sharedFs = Object.assign({}, realFs, { readFileSync: (f, ...rest) => (f === 0 ? STDIN : realFs.readFileSync(f, ...rest)) });
const sharedRequire = (m) => (m === 'fs' ? sharedFs : require(m));
// A non-zero exit ends the process at once (a check's own try/catch must not swallow
// a deny); exit(0) only leaves that check.
function runCheck(body) {
  let out = '';
  const fakeProcess = new Proxy(process, {
    get: (t, k) => (k === 'exit' ? (c) => { if (c) { if (out) process.stdout.write(out); process.exit(c); } throw new HookExit(0); } : typeof t[k] === 'function' ? t[k].bind(t) : t[k]),
  });
  const fakeConsole = Object.assign(Object.create(console), { log: (...a) => { out += require('util').format(...a) + '\n'; } });
  try {
    body(sharedRequire, fakeProcess, fakeConsole);
    return { out };
  } catch (e) {
    if (e instanceof HookExit) return { out };
    console.error('hook check failed: ' + (e && e.message));
    return { out };
  }
}
const CHECKS = [
(require, process, console) => {
const fs = require('fs');

let payload = {};
try {
  const stdinText = fs.readFileSync(0, 'utf-8');
  payload = JSON.parse(stdinText);
} catch (err) {
  // If parsing fails but stdin was read, we proceed
}

const toolInput = payload.tool_input || {};
const cmd = toolInput.command || '';
const cwd = payload.cwd || process.cwd();

const shellInvocations = (() => {
  try {
    const module = { exports: {} };
    (function (module, exports) {
/*! unbash@6.0.4 - ISC - Copyright (c) Lars Kappert. Bundled with the fediim shell guard walker. */
"use strict";var oe=Object.defineProperty;var Ve=Object.getOwnPropertyDescriptor;var Ge=Object.getOwnPropertyNames;var $e=Object.prototype.hasOwnProperty;var Ke=(r,t)=>{for(var e in t)oe(r,e,{get:t[e],enumerable:!0})},ze=(r,t,e,i)=>{if(t&&typeof t=="object"||typeof t=="function")for(let s of Ge(t))!$e.call(r,s)&&s!==e&&oe(r,s,{get:()=>t[s],enumerable:!(i=Ve(t,s))||i.enumerable});return r};var Xe=r=>ze(oe({},"__esModule",{value:!0}),r);var ws={};Ke(ws,{commandInvocations:()=>bs});module.exports=Xe(ws);function Mt(r,t){switch(r.type){case"Script":case"CompoundList":case"AndOr":for(let e of r.commands)t(e);return;case"Statement":t(r.command);return;case"Command":for(let e of r.prefix)t(e);r.name&&t(r.name);for(let e of r.suffix)t(e);return;case"Pipeline":for(let e of r.prefixes)t(e);for(let e of r.commands)t(e);return;case"Redirected":t(r.command);for(let e of r.redirects)t(e);return;case"If":t(r.clause),t(r.then);for(let e of r.elif)t(e);r.else&&t(r.else);return;case"Elif":t(r.clause),t(r.then);return;case"For":case"Select":if(t(r.name),r.wordlist)for(let e of r.wordlist)t(e);t(r.body);return;case"While":t(r.clause),t(r.body);return;case"Function":case"Coproc":r.name&&t(r.name),t(r.body);return;case"Subshell":case"BraceGroup":t(r.body);return;case"Case":t(r.word);for(let e of r.items)t(e);return;case"CaseItem":for(let e of r.pattern)t(e);t(r.body);return;case"ArithmeticFor":{let e=r.initialize;e&&t(e);let i=r.test;i&&t(i);let s=r.update;s&&t(s),t(r.body);return}case"Word":case"Glob":case"ArithmeticWord":case"HereDocBody":case"BraceExpansion":case"ExtendedGlob":{let e=r.parts;if(e)for(let i of e)t(i);return}case"DoubleQuoted":case"LocaleString":for(let e of r.parts)t(e);return;case"ParameterExpansion":r.index&&t(r.index),r.operation&&t(r.operation);return;case"Default":case"Remove":case"Transform":t(r.operand);return;case"CaseModification":r.operand&&t(r.operand);return;case"Replace":t(r.pattern),t(r.replacement);return;case"Slice":t(r.offset),r.length&&t(r.length);return;case"Assignment":{let e=r.index;e&&t(e),t(r.value);return}case"ArrayValue":for(let e of r.elements)t(e);return;case"Redirect":case"HereString":r.descriptor&&t(r.descriptor),r.target&&t(r.target);return;case"HereDoc":r.descriptor&&t(r.descriptor),r.delimiter&&t(r.delimiter),t(r.body);return;case"CommandExpansion":case"ProcessSubstitution":case"ArithmeticCommandExpansion":{let e=r.script;e&&t(e);return}case"ArithmeticExpansion":case"ArithmeticCommand":case"ArithmeticGroup":case"TestCommand":case"TestGroup":{let e=r.expression;e&&t(e);return}case"ArithmeticBinary":case"TestBinary":t(r.left),t(r.right);return;case"ArithmeticUnary":case"TestUnary":t(r.operand);return;case"ArithmeticTernary":t(r.test),t(r.consequent),t(r.alternate);return;case"TestLogical":for(let e of r.operands)t(e);return;case"TestNot":for(let e of r.negations)t(e);t(r.operand);return;case"Literal":case"SingleQuoted":case"AnsiCQuoted":case"SimpleExpansion":case"Names":case"Unknown":case"FileDescriptor":case"FileDescriptorVariable":case"HereDocDelimiter":case"Time":case"Negation":return;default:{let e=r;throw new TypeError(`Unexpected syntax node: ${e}`)}}}function ge(r){return r>=48&&r<=55}function _e(r){return r>=48&&r<=57||r>=65&&r<=70||r>=97&&r<=102}function Je(r,t){try{return String.fromCodePoint(r)}catch{return t}}var Ye=new TextDecoder("utf-8",{ignoreBOM:!0});function ke(r){return Ye.decode(new Uint8Array(r))}function Ze(r,t,e){if(r.charCodeAt(t)!==92||t+2>e)return;let i=r.charCodeAt(t+1),s=t+2,o=s;if(i===120){for(;o<e&&o-s<2&&_e(r.charCodeAt(o));)o++;return o===s?void 0:{value:Number.parseInt(r.slice(s,o),16),end:o}}if(ge(i)){for(;o<e&&o-s<2&&ge(r.charCodeAt(o));)o++;return{value:Number.parseInt(r.slice(t+1,o),8)&255,end:o}}}function Qt(r,t,e){let i=t,s="",o=[],n=-1;for(;i<e&&r.charCodeAt(i)!==39;){let p=i,c=Ze(r,i,e);if(c&&c.value>=128){o.push(c.value),i=c.end;continue}if(o.length>0&&(s+=ke(o),o=[]),c){c.value===0&&n===-1&&(n=s.length),s+=String.fromCharCode(c.value),i=c.end;continue}if(r.charCodeAt(i)!==92||i+1>=e){let l=i;for(;i<e;){let m=r.charCodeAt(i);if(m===39||m===92&&i+1<e)break;i++}s+=r.slice(l,i);continue}i++;let d=r[i++];switch(d){case"a":s+="\x07";break;case"b":s+="\b";break;case"e":case"E":s+="\x1B";break;case"f":s+="\f";break;case"n":s+=`
`;break;case"r":s+="\r";break;case"t":s+="	";break;case"v":s+="\v";break;case"\\":s+="\\";break;case"'":s+="'";break;case'"':s+='"';break;case"?":s+="?";break;case`
`:break;case"c":{let l=i<e?r.charCodeAt(i):39;if(l===39){s+=r.slice(p,i);break}if(i++,l===92){let A=i<e&&r.charCodeAt(i)===92;A&&i++,s+="",!A&&i<e&&(s+=r[i],i++);break}let m=l===63?127:l&31;m===0&&l<128&&n===-1&&(n=s.length),s+=String.fromCharCode(m);break}case"u":case"U":{let l=i,m=d==="u"?4:8;for(;i<e&&i-l<m&&_e(r.charCodeAt(i));)i++;if(i===l){s+=`\\${d}`;break}let A=r.slice(p,i),k=Number.parseInt(r.slice(l,i),16);k===0&&n===-1&&(n=s.length),s+=Je(k,A);break}default:s+=`\\${d}`;break}}o.length>0&&(s+=ke(o)),n!==-1&&(s=s.slice(0,n));let h=i<e;return h&&i++,{value:s,end:i,closed:h}}var pt=class r{static _resolveScript;type;pos;end;text;#t;#e=void 0;constructor(t,e,i,s,o){this.type=t,this.pos=e,this.end=i,this.text=s,this.#t=o}get script(){let t=this.#t;if(t!==void 0){let e=r._resolveScript(t.source,t.start,t.end,t.depth,t.parenBoundary,t.context);t.context===void 0&&(e.source=t.source),this.script=e}return this.#e}set script(t){this.#e=t,this.#t=void 0}toJSON(){return{type:this.type,pos:this.pos,end:this.end,text:this.text,script:this.script}}},Xt=class extends pt{operator;constructor(t,e,i,s,o){super("ProcessSubstitution",t,e,i,o),this.operator=s}toJSON(){return{type:this.type,pos:this.pos,end:this.end,text:this.text,operator:this.operator,script:this.script}}},Nt=class r{static _resolveExpression;type="ArithmeticExpansion";pos;end;text;#t;#e=void 0;constructor(t,e,i,s){this.pos=t,this.end=e,this.text=i,this.#t=s}get expression(){let t=this.#t;return t!==void 0&&(this.expression=r._resolveExpression(t.body,t.offset,t.source,t.depth,t.context,t.depth+1)),this.#e}set expression(t){this.#e=t,this.#t=void 0}toJSON(){return{type:this.type,pos:this.pos,end:this.end,text:this.text,expression:this.expression}}};function je(r){let t="";for(let e of r)t+=e.type==="Literal"?e.value:e.text;return t}function Jt(r){let t=r.indexOf("\\");if(t===-1)return r;let e="",i=0;for(let s=t;s<r.length;s++)if(r.charCodeAt(s)===92){if(e+=r.slice(i,s),s++,s>=r.length){e+="\\",i=s;break}r.charCodeAt(s)!==10&&(e+=r[s]),i=s+1}return e+r.slice(i)}function Ee(r){let t=1;for(;r[t]==="\\"&&r[t+1]===`
`;)t+=2;return t===1||r[t]!=="("?r:r[0]+r.slice(t)}function Yt(r){let t="";for(let e of r)switch(e.type){case"Literal":case"Glob":case"SingleQuoted":case"AnsiCQuoted":t+=e.value;break;case"DoubleQuoted":case"LocaleString":t+=je(e.parts);break;case"CommandExpansion":t+=e.text[0]==="$"?Ee(e.text):e.text;break;case"ExtendedGlob":t+=Ee(e.text);break;default:t+=e.text;break}return t}var Z=class{type="Word";text;pos;end;#t;#e;#i;#s;#n=null;constructor(t,e,i,s,o,n=0){this.text=t,this.pos=e,this.end=i,this.#t=s,this.#e=o,this.#i=n,this.#s=o===void 0?void 0:null}get value(){if(this.#n===null){let t=this.#t===void 0&&this.#s===null?void 0:this.parts;this.#n=t?Yt(t):Jt(this.text)}return this.#n}get parts(){return this.#s===null&&(this.#s=this.#e?.(this.#t??"",this,this.#i)??void 0),this.#s}set parts(t){this.#s=t??void 0}toJSON(){return{type:this.type,text:this.text,pos:this.pos,end:this.end,parts:this.parts,value:this.value}}};function Wt(r){return{boundaries:r===void 0?void 0:re(r),wordParts:void 0,embeddedParts:void 0,arrayParts:void 0}}function re(r){return r.boundaries??(r.boundaries=new Map)}function ht(r,t,e){for(;t+1<e&&r.charCodeAt(t)===92&&r.charCodeAt(t+1)===10;)t+=2;return t}function St(r){return/[*?[]/.test(r)}var xt=new Uint8Array(128);xt[33]=1;xt[94]=1;xt[91]=1;xt[93]=1;xt[58]=1;xt[46]=1;xt[61]=1;xt[45]=1;xt[92]=1;function ts(r,t,e,i,s){let o=e-1;for(;o>t&&r.charCodeAt(o)!==93;)o--;if(o===t)return null;let n=[],h=[],p=[],c=0,d=!1;for(let C=t;C<e;C++){for(;i&&c<i.length&&i[c].end<=C+s;)c++;let E=i?.[c],b=C,_=0;if(E&&E.pos<=C+s&&E.type!=="Literal")C=E.end-s-1;else{if(_=r.charCodeAt(C),_===92&&C+1<e&&r.charCodeAt(C+1)===10){C++;continue}(_>=128||!xt[_])&&(_=0)}let g=h.length-1;if(_===0&&g>=0&&h[g]===0){p[g]++;continue}g>=0&&h[g]===91&&(_===58||_===61||_===46)&&(d=!0),n.push(b),h.push(_),p.push(1)}let l=h.length,m;if(d){m=new Int32Array(l).fill(-1);let C=-1,E=-1,b=-1,_=-1;for(let g=l-1;g>=0;g--){let y=h[g+2];if(y===93?C=g+2:h[g+3]===93&&(y===58?E=g+2:y===61?b=g+2:y===46&&(_=g+2)),h[g]!==91)continue;let P=h[g+1],w=P===58?E:P===61?b:P===46?_:-1;w!==-1&&(P===46||C===-1||C>w)&&(m[g]=w+2)}}let A=C=>m===void 0?-1:m[C],k=new Int32Array(l+2).fill(-1),x=(C,E)=>{if(C>=l)return-1;if(E&&h[C]===45&&C+1<l&&h[C+1]!==93){let b=C+1;if(h[b]===92){if(b+1>=l)return-1;b++}let _=b+1;if(h[b]===91&&h[b+1]===46){let g=A(b);g!==-1&&(_=g)}else if(h[b]===0&&p[b]>1)return k[b];return _>=l?-1:h[_]===93?_+1:k[_]}return h[C]===93?C+1:k[C]};for(let C=l-1;C>=0;C--){let E=h[C],b=C+1,_=!0;if(E===92){if(C+1>=l)continue;b=C+2}else if(E===91){let g=h[C+1];if(g===58||g===61||g===46){let y=A(C);y!==-1&&(b=y,_=g===46)}}k[C]=x(b,_)}let f=new Map;for(let C=0;C<l;C++){if(h[C]!==91)continue;let E=C+1;if((h[E]===33||h[E]===94)&&E++,E>=l)continue;let b=h[E]===93?x(E+1,!0):k[E];b!==-1&&f.set(n[C],n[b-1]+1)}return f}function he(r,t,e,i,s,o,n){for(;t<e;){for(;i&&s<i.length&&i[s].end<=t+o;)s++;let h=i?.[s];if(h&&h.pos===t+o&&h.end<=e+o){n.push(h),t=h.end-o,s++;continue}let p=h?Math.min(e,(h.pos>t+o?h.pos:h.end)-o):e,c=r.slice(t,p);n.push({type:"Literal",pos:t+o,end:p+o,text:c,value:Jt(c)}),t=p}return s}function At(r,t,e,i,s=0){let o,n,h=0,p=0,c=t;for(let d=t;d<e;d++){for(;i&&h<i.length&&i[h].end<=d+s;)h++;let l=i?.[h];if(l&&l.pos<=d+s&&l.type!=="Literal"){d=l.end-s-1;continue}let m=r.charCodeAt(d);if(m===92){d++;continue}let A;if(m===42||m===63){let f=l?Math.min(e,l.end-s):e,C=ht(r,d+1,f);if(C<f&&r.charCodeAt(C)===40)continue;if(A=d+1,m===42)for(;A<f&&r.charCodeAt(A)===42;){let E=ht(r,A+1,f);if(E<f&&r.charCodeAt(E)===40)break;A++}}else if(m===91){n===void 0&&(n=ts(r,d,e,i,s));let f=n?.get(d);if(f===void 0)continue;A=f}else continue;o||(o=[]),p=he(r,c,d,i,p,s,o);let k=r.slice(d,A),x;if(m===91){let f=[];p=he(r,d+1,A-1,i,p,s,f),x={type:"Glob",kind:"bracket",pos:d+s,end:A+s,text:k,value:"["+Yt(f)+"]",parts:f}}else x={type:"Glob",kind:m===42?"star":"question",pos:d+s,end:A+s,text:k,value:k,parts:void 0};o.push(x),c=A,d=A-1}return o?(he(r,c,e,i,p,s,o),o):i??void 0}var a={Word:0,Assignment:1,Semi:2,Newline:3,Pipe:4,And:5,Or:6,Amp:7,LParen:8,RParen:9,LBrace:10,RBrace:11,Bang:12,If:13,Then:14,Else:15,Elif:16,Fi:17,Do:18,Done:19,For:20,While:21,Until:22,In:23,Case:24,Esac:25,Function:26,DoubleSemi:27,SemiAmp:28,DoubleSemiAmp:29,Select:30,DblLBracket:31,DblRBracket:32,EOF:33,ArithCmd:34,Coproc:35,Redirect:36},Zt=class{token=a.EOF;_value="";_owner;pos=0;end=0;fileDescriptor=void 0;variableName=void 0;descriptorEnd=0;delimiterQuoted=!1;content=void 0;targetPos=0;targetEnd=0;assignmentOperatorPos=-1;raw=!1;keywordEligible=!1;constructor(t=null){this._owner=t}get value(){return this._value??(this._value=this._owner===null?"":this._owner._tokenValue(this.pos,this.end,this.raw))}set value(t){this._value=t}reset(){this.token=a.EOF,this._value="",this.pos=0,this.end=0,this.fileDescriptor=void 0,this.variableName=void 0,this.descriptorEnd=0,this.delimiterQuoted=!1,this.content=void 0,this.targetPos=0,this.targetEnd=0,this.assignmentOperatorPos=-1,this.raw=!1,this.keywordEligible=!1}copyFrom(t){this.token=t.token,this._value=t._value,this.pos=t.pos,this.end=t.end,this.fileDescriptor=t.fileDescriptor,this.variableName=t.variableName,this.descriptorEnd=t.descriptorEnd,this.delimiterQuoted=t.delimiterQuoted,this.content=t.content,this.targetPos=t.targetPos,this.targetEnd=t.targetEnd,this.assignmentOperatorPos=t.assignmentOperatorPos,this.raw=t.raw,this.keywordEligible=t.keywordEligible}},be=new Map([["if",a.If],["then",a.Then],["else",a.Else],["elif",a.Elif],["fi",a.Fi],["do",a.Do],["done",a.Done],["for",a.For],["while",a.While],["until",a.Until],["in",a.In],["case",a.Case],["esac",a.Esac],["function",a.Function],["select",a.Select],["coproc",a.Coproc],["!",a.Bang],["{",a.LBrace],["}",a.RBrace]]);var M=new Uint8Array(128);M[124]=1;M[38]=1;M[59]=1;M[40]=1;M[41]=1;M[60]=1;M[62]=1;M[32]=1;M[9]=1;M[10]=1;M[92]=2;M[39]=2;M[34]=2;M[36]=2;M[96]=2;M[123]=2;function Ut(r,t,e){for(;t<e;){let i=r.charCodeAt(t);if(i===32||i===9)t++;else if(i===92&&t+1<e&&r.charCodeAt(t+1)===10)t+=2;else break}return t}var ve=new Uint8Array(128);for(let r of[9,10,32,33,37,38,40,41,42,43,44,45,47,58,60,61,62,63,94,124])ve[r]=1;function It(r,t,e){for(let i=t;i<e;i++){let s=r.charCodeAt(i);if(s===92||s===39||s===34||s===36||s===96||(s===60||s===62)&&i+1<e&&r.charCodeAt(i+1)===40)return!0}return!1}function we(r,t,e=!1,i){let s=0,o=0;for(let n=0;n<r.length;n++){let h=r.charCodeAt(n);if(h===92){n++;continue}if(h===123){s++;continue}if(h===125){s>0&&s--;continue}if(h===39){for(n++;n<r.length&&r.charCodeAt(n)!==39;)n++;continue}if(h===34){for(n++;n<r.length;){let c=r.charCodeAt(n);if(c===34)break;if(c===92){n+=2;continue}let d=i?.(n,!0)??n;if(d>n){n=d;continue}n++}continue}let p=i?.(n,!1)??n;if(p>n){n=p-1;continue}if(e&&s===0){if(h===63){o++;continue}if(h===58&&o>0){o--;continue}}if(h===t&&s===0)return n}return-1}var Et=new Uint8Array(128);for(let r=97;r<=122;r++)Et[r]=3;for(let r=65;r<=90;r++)Et[r]=3;for(let r=48;r<=57;r++)Et[r]=2;Et[95]=3;var bt=new Uint8Array(128);bt[63]=1;bt[64]=1;bt[42]=1;bt[43]=1;bt[33]=1;bt[61]=1;function ye(r){return r<128&&r!==61&&bt[r]===1}var es={63:"?",64:"@",42:"*",43:"+",33:"!"};function Pe(r){let t=r.type;return t==="Literal"||t==="SimpleExpansion"||t==="ParameterExpansion"||t==="CommandExpansion"||t==="ArithmeticExpansion"}function ss(r,t,e){for(let i=t;i<e;i++){let s=r.charCodeAt(i);if(s<48||s>57)return!1}return e>t}var ut=-1,ce=0,Se=1,de=2,De=3,it=4;function is(r,t){let e=r[r.length-1],i=t[0],s=0;e?.type==="Literal"&&i?.type==="Literal"&&e.end===i.pos&&(r[r.length-1]={type:"Literal",pos:e.pos,end:i.end,text:e.text+i.text,value:e.value+i.value},s=1);for(let o=s;o<t.length;o++)r.push(t[o]);return r}function Te(r){return r<ut}function Be(r){return-r-2}function ae(r,t,e,i){let s=i;for(let o=t;o<e&&s>=0;o++){let n=r.charCodeAt(o);if(s>=it)n===91?s++:n===93&&--s===it&&(s=de);else if(s===ce)s=n<128&&Et[n]&1?Se:ut;else if(s===Se){if(n<128&&Et[n]&2)continue;n===91?s=it+1:n===43?s=De:s=n===61?-o-2:ut}else s===de?n===43?s=De:s=n===61?-o-2:ut:s=n===61?-o-2:ut}return s}function $(r,t,e,i=0,s=0){r.token=t,r._value=e,r.pos=i,r.end=s,r.fileDescriptor=void 0,r.variableName=void 0,r.descriptorEnd=0,r.delimiterQuoted=!1,r.content=void 0,r.assignmentOperatorPos=-1,r.raw=!1,r.keywordEligible=!1}function Dt(r,t,e,i,s){r.token=t,r._value=null,r.pos=e,r.end=i,r.fileDescriptor=void 0,r.variableName=void 0,r.descriptorEnd=0,r.delimiterQuoted=!1,r.content=void 0,r.assignmentOperatorPos=-1,r.raw=s,r.keywordEligible=!1}function He(r,t,e){switch(r.charCodeAt(t)){case 33:return e===1?a.Bang:void 0;case 123:return e===1?a.LBrace:void 0;case 125:return e===1?a.RBrace:void 0;case 105:{if(e!==2)return;let i=r.charCodeAt(t+1);return i===102?a.If:i===110?a.In:void 0}case 102:return e===2?r.charCodeAt(t+1)===105?a.Fi:void 0:e===3?r.startsWith("for",t)?a.For:void 0:e===8&&r.startsWith("function",t)?a.Function:void 0;case 116:return e===4&&r.startsWith("then",t)?a.Then:void 0;case 101:return e!==4?void 0:r.startsWith("else",t)?a.Else:r.startsWith("elif",t)?a.Elif:r.startsWith("esac",t)?a.Esac:void 0;case 100:return e===2?r.charCodeAt(t+1)===111?a.Do:void 0:e===4&&r.startsWith("done",t)?a.Done:void 0;case 99:return e===4?r.startsWith("case",t)?a.Case:void 0:e===6&&r.startsWith("coproc",t)?a.Coproc:void 0;case 119:return e===5&&r.startsWith("while",t)?a.While:void 0;case 117:return e===5&&r.startsWith("until",t)?a.Until:void 0;case 115:return e===6&&r.startsWith("select",t)?a.Select:void 0;default:return}}var u={Normal:0,CommandStart:1,TestMode:2,CommandPrefix:3,Declaration:4,ArrayElement:5,FunctionName:6,CasePattern:7,EvalArgument:8};function ns(r){return r===32||r===9||r===10||r===124}var ct=class r{static _findSubstitutionEnd;src;srcEnd;parenBoundary;pos;current;nextState;hasPeek;pendingHereDocs;substitutionContext;_errors=null;_buildParts=!1;_buildValue=!1;_nestingDepth=0;constructor(t,e=0,i=t.length,s=!1,o=Wt()){if(this.src=t,this.srcEnd=i,this.parenBoundary=s,this.pos=e,this.current=new Zt(this),this.nextState=new Zt(this),this.hasPeek=!1,this.pendingHereDocs=null,this.substitutionContext=o,e===0&&t.charCodeAt(0)===35&&t.charCodeAt(1)===33){let n=t.indexOf(`
`);this.pos=n===-1?this.srcEnd:n+1}}getSource(){return this.src}fork(t,e,i=!1){let s=new r(this.src,t,e,i,this.substitutionContext);return s._nestingDepth=this._nestingDepth,s}getSubstitutionContext(){return this.substitutionContext}get errors(){return this._errors??(this._errors=[])}commandExpansion(t,e,i,s,o=!1){let n=this.scriptWindow(this.src,i,i+s.length,o);return new pt("CommandExpansion",t,this.pos,e,n)}decodedCommandExpansion(t,e,i){let s=this.scriptWindow(i,0,i.length,!1,!0);return new pt("CommandExpansion",t,this.pos,e,s)}processSubstitution(t,e,i,s){let o=this.scriptWindow(this.src,t+2,t+2+s.length,!this._unbalanced);return new Xt(t,this.pos,e,i,o)}scriptWindow(t,e,i,s,o=!1){if(this._nestingDepth>256)return;let n=o?void 0:this.substitutionContext;return{source:t,start:e,end:i,depth:this._nestingDepth+1,parenBoundary:s,context:n}}getPos(){return this.pos}findSubstitutionEnd(t,e,i=this._nestingDepth+1){return r._findSubstitutionEnd===void 0?-1:r._findSubstitutionEnd(this.src,t,e,i,this.substitutionContext)}_tokenValue(t,e,i){return i?this.src.slice(t,e):this.wordValueOf(t,e)}wordValueOf(t,e){let i=this.pos,s=this.srcEnd,o=this._bracketWindow,n=this._arithmeticBracketWindow,h=this._buildValue,p=this._unbalanced,c=this._errors===null?0:this._errors.length;this.pos=t,this.srcEnd=e,this._buildValue=!0,this.readWordText(),this.pos<e&&this._wordAssignmentState!==void 0&&this._wordAssignmentState>=it&&(this.pos=t,this.readWordText(!0));let d=this._wordText;return this.pos=i,this.srcEnd=s,this._bracketWindow=o,this._arithmeticBracketWindow=n,this._buildValue=h,this._unbalanced=p,this._errors!==null&&(this._errors.length=c),d}findClosingBracket(t,e=this.srcEnd){let i=Math.min(e,this.srcEnd),s=this._bracketWindow;if((!s||s.limit!==i)&&(s={limit:i,depth:this._nestingDepth,last:this.src.lastIndexOf("]",i-1)},this._bracketWindow=s),t>s.last||(s.depth!==this._nestingDepth&&(s={limit:i,depth:this._nestingDepth,last:s.last},this._bracketWindow=s),s.failed?.has(t)))return-1;let o=[],n=this.findClosingShellDelimiter(t,e,93,!1,!0,o);if(n===-1){let h=s.failed??(s.failed=new Set);for(let p of o)for(let c of p)h.add(c)}return n}findClosingArithmeticBracket(t,e=this.srcEnd){let i=Math.min(e,this.srcEnd),s=this._arithmeticBracketWindow;return(!s||s.end!==i||s.depth!==this._nestingDepth)&&(s={end:i,depth:this._nestingDepth,failed:new Set},this._arithmeticBracketWindow=s),s.failed.has(t)?-1:this.findClosingShellDelimiter(t,e,93,!1,!1,void 0,s.failed)}findClosingBrace(t,e=this.srcEnd){return this.findClosingShellDelimiter(t,e,125)}findClosingParenthesis(t,e=this.srcEnd){let i=this.pos,s=this.srcEnd,o=this._bracketWindow,n=this._arithmeticBracketWindow,h=this._unbalanced;this.pos=t,this.srcEnd=Math.min(e,this.srcEnd),this.extractBalanced();let p=this._unbalanced?-1:this.pos-1;return this.pos=i,this.srcEnd=s,this._bracketWindow=o,this._arithmeticBracketWindow=n,this._unbalanced=h,p}findArithmeticExpansionEnd(t,e=this.srcEnd){let i=this.fork(t,e);return i.pos=t+1,i.scanArithmeticBody(),i.pos}findArithmeticWordEnd(t,e=this.srcEnd){let i=this.fork(t,e);return i.pos=t,i.scanArithmeticWordEnd()}scanArithmeticWordEnd(){for(;this.pos<this.srcEnd;){let t=this.src.charCodeAt(this.pos);if(t===36){this.readDollar();continue}if(t===96){this.readBacktickExpansion();continue}if(t===39){this.pos++,this.skipSQ();continue}if(t===34){this.pos++,this.skipDQ();continue}if(t===92){this.pos+=2;continue}if(t===91){let e=this.findClosingBracket(this.pos+1);if(e!==-1){this.pos=e+1;continue}}if((t===60||t===62)&&this.src.charCodeAt(this.pos+1)===40){this.pos+=2,this.extractBalanced();continue}if(t<128&&ve[t])break;this.pos++}return this.pos}findClosingShellDelimiter(t,e,i,s=!1,o=!0,n,h){let p=this.pos,c=this.srcEnd,d=this._bracketWindow,l=this._arithmeticBracketWindow,m=this._unbalanced;this.srcEnd=Math.min(e,this.srcEnd);let A=[i];n?.push([]);let k=h?[t]:void 0,x=t,f=!0,C=-1;for(;x<this.srcEnd;){let E=A[A.length-1];n&&E===93&&n[n.length-1].push(x);let b=this.src.charCodeAt(x);if(b===92){x+1<this.srcEnd&&this.src.charCodeAt(x+1)!==10&&(f=!1),x+=2;continue}if(b===35&&s&&A.length===1&&f){for(;x<this.srcEnd&&this.src.charCodeAt(x)!==10;)x++;continue}if(b===39){this.pos=x+1,this.skipSQ(),x=this.pos,f=!1;continue}if(b===34){this.pos=x+1,this.skipDQ(),x=this.pos,f=!1;continue}if(b===96){for(x++;x<this.srcEnd&&this.src.charCodeAt(x)!==96;)this.src.charCodeAt(x)===92&&x++,x++;x<this.srcEnd&&x++,f=!1;continue}if(b===36&&x+1<this.srcEnd&&this.src.charCodeAt(x+1)===40||(b===60||b===62)&&x+1<this.srcEnd&&this.src.charCodeAt(x+1)===40){this.pos=x+2,this.extractBalanced(),x=this.pos,f=!1;continue}if(b===36&&x+1<this.srcEnd){let _=this.src.charCodeAt(x+1);if(_===39){x=Qt(this.src,x+2,this.srcEnd).end,f=!1;continue}if(_===36){x+=2,f=!1;continue}if(_===123&&o){A.push(125),n?.push([]),x+=2,f=!1;continue}}if(s&&A.length===1&&(b===91&&f?(A.push(93),n?.push([])):C===-1&&(b===59||b===38||b===124||b===60||b===62||b===40&&!ye(this.src.charCodeAt(x-1)))&&(C=x)),E===93&&b===91)A.push(93),n?.push([]),k?.push(x+1);else if(E===41&&b===40)A.push(41),n?.push([]);else if(b===E){if(A.pop(),k?.pop(),n?.pop(),A.length===0)return this.pos=p,this.srcEnd=c,this._bracketWindow=d,this._arithmeticBracketWindow=l,this._unbalanced=m,C!==-1&&this.errors.push({message:`unexpected '${this.src[C]}' in array assignment`,pos:C}),x;f=!1,x++;continue}f=b<128&&(M[b]&1)!==0,x++}if(k)for(let E of k)h.add(E);return this.pos=p,this.srcEnd=c,this._bracketWindow=d,this._arithmeticBracketWindow=l,this._unbalanced=m,-1}scanBraceExpansion(t,e){let i=this.src,s=this.srcEnd,o=e.get(t);if(o!==void 0)return o;let n=this.pos,h=this._unbalanced,p=this._errors===null?0:this._errors.length,c=[t,0],d=t+1;for(;d<s;){let l=i.charCodeAt(d),m=l<128?M[l]:0;if(m===0){if(d++,l===125){let k=c.pop();if(e.set(c.pop(),k?d:-1),c.length===0)break}else(l===44||l===46&&i.charCodeAt(d)===46)&&(c[c.length-1]=1);continue}let A=d+1<s?i.charCodeAt(d+1):0;if(m&1)if(A===40&&(l===60||l===62))this.pos=d+2,this.extractBalanced(),d=this.pos;else if(l===40&&ye(i.charCodeAt(d-1))){let k=this.findClosingShellDelimiter(d+1,s,41);if(k===-1)break;d=k+1}else break;else if(l===92)d+=2;else if(l===39)this.pos=d+1,this.skipSQ(),d=this.pos;else if(l===34)this.pos=d+1,this.skipDQ(),d=this.pos;else if(l===96){for(d++;d<s&&i.charCodeAt(d)!==96;)d+=i.charCodeAt(d)===92?2:1;d++}else if(l===123)c.push(d,0),d++;else if(A===39)d=Qt(i,d+2,s).end;else if(A===123){let k=this.findClosingBrace(d+2,s);if(k===-1)break;d=k+1}else A===40?(this.pos=d+2,this.extractBalanced(),d=this.pos):d++}this.pos=n,this._unbalanced=h,this._errors!==null&&(this._errors.length=p);for(let l=0;l<c.length;l+=2)e.set(c[l],-1);return e.get(t)}skipSubshellBody(){return this.extractBalanced(!1),this._unbalanced?-1:this.pos}skipCompoundBody(t){let e=[{close:t,phase:t===a.Esac?"case-pattern":"commands"}],i=!0;for(;;){let s=e[e.length-1].phase,o=i?u.CommandStart:u.Normal;s==="function-name"?o=u.FunctionName:s==="case-pattern"&&i&&(o=u.CasePattern);let n=this.next(o),h=n.token;if(h===a.EOF)return-1;let p=e.length-1,c=e[p];if(c.phase==="function-name"){if(h===a.Newline)continue;c.phase="function-body",i=!0;continue}else if(c.phase==="function-body"){if(h===a.Newline)continue;if(c.phase="commands",i=!0,h===a.LParen&&this.peek(u.Normal).token===a.RParen){this.next(u.Normal),c.phase="function-body";continue}}else if(c.phase==="coproc-command"){if(h===a.Newline)continue;if(h===a.Word){c.phase="coproc-body",i=!0;continue}c.phase="commands",i=!0}else if(c.phase==="coproc-body"){if(h===a.Newline||(c.phase=h===a.Word&&n.keywordEligible&&n.value==="time"?"time-command":"commands",i=!0,c.phase==="time-command"))continue}else if(c.phase==="time-command"){if(h===a.Word&&n.keywordEligible&&n.value==="-p"){c.phase="time-command-after-p";continue}if(h===a.Word&&n.keywordEligible&&n.value==="--"){c.phase="commands";continue}c.phase="commands",i=!0}else if(c.phase==="time-command-after-p"){if(h===a.Word&&n.keywordEligible&&n.value==="--"){c.phase="commands";continue}c.phase="commands",i=!0}else if(c.phase==="for-header"){if(h===a.ArithCmd||h===a.Semi||h===a.Newline){i=!0;continue}if(h===a.Do||h===a.LBrace){c.close=h===a.Do?a.Done:a.RBrace,c.phase="commands",i=!0;continue}}else if(c.phase==="case-word"){if(h===a.Newline)continue;c.phase="case-in",i=!1;continue}else if(c.phase==="case-in"){if(h===a.Newline){i=!0;continue}c.phase="case-pattern",i=!0;continue}else if(c.phase==="case-pattern"){if(h===a.Esac&&i){if(e.pop(),e.length===0)return n.end;i=!1;continue}h===a.RParen?(c.phase="commands",i=!0):i=h===a.Newline;continue}if(h===c.close){if(e.pop(),e.length===0)return n.end;i=!1;continue}if(i)switch(h){case a.LParen:e.push({close:a.RParen,phase:"commands"});break;case a.LBrace:e.push({close:a.RBrace,phase:"commands"});break;case a.If:e.push({close:a.Fi,phase:"commands"});break;case a.For:e.push({close:a.Done,phase:"for-header"});break;case a.While:case a.Until:case a.Select:e.push({close:a.Done,phase:"commands"});break;case a.Case:e.push({close:a.Esac,phase:"case-word"});break;case a.DblLBracket:if(!this.skipTestCommandBody())return-1;i=!1;continue;case a.Assignment:case a.Redirect:case a.Bang:case a.Then:case a.Else:case a.Elif:case a.Do:case a.In:break;case a.Semi:case a.Newline:case a.Pipe:case a.And:case a.Or:case a.Amp:case a.DoubleSemi:case a.SemiAmp:case a.DoubleSemiAmp:break;case a.Function:c.phase="function-name";break;case a.Coproc:c.phase="coproc-command";break;default:h===a.Word&&n.keywordEligible&&n.value==="time"?(c.phase="time-command",i=!0):i=!1;continue}switch(h){case a.Semi:case a.Newline:case a.Pipe:case a.And:case a.Or:case a.Amp:i=!0;break;case a.DoubleSemi:case a.SemiAmp:case a.DoubleSemiAmp:c.close===a.Esac&&(c.phase="case-pattern"),i=!0;break;case a.RParen:i=!0;break}}}skipTestGroup(){let t=1;for(;;){let e=this.next(u.TestMode);if(e.token===a.EOF)return-1;if(e.token===a.DblRBracket)return this.unshift(e),-1;if(e.token===a.LParen)t++;else if(e.token===a.RParen&&--t===0)return e.end}}skipTestCommandBody(){for(;;){let t=this.next(u.TestMode).token;if(t===a.DblRBracket)return!0;if(t===a.EOF)return!1}}buildWordParts(t){this._buildParts=!0,this.pos=t;let e=this.src.charCodeAt(t);if((e===60||e===62)&&t+1<this.srcEnd&&this.src.charCodeAt(t+1)===40){this.pos=t+2;let i=this.extractBalanced(),s=this.src.slice(t,this.pos),o=this.processSubstitution(t,s,e===60?"<":">",i);this.pos<this.srcEnd?(this.readWordText(),this._wordParts?this._wordParts.unshift(o):this._wordParts=[o]):this._wordParts=[o]}else this.readWordText(),this.pos<this.srcEnd&&this._wordAssignmentState!==void 0&&this._wordAssignmentState>=it&&(this.pos=t,this.readWordText(!0));return this._wordParts}buildEmbeddedWordParts(t,e=!1){return this._buildParts=!0,this.pos=t,this.readInnerWordText(!1,e),this._wordParts}buildArrayElementParts(t){let e=this.findClosingBracket(t+1);if(e===-1)return this.buildWordParts(t);let i=this.src;this._buildParts=!0;let s=It(i,t,e+1)?this.parseSubFieldWord(t,e+1,"embedded"):void 0;this.pos=e+1,this.readWordText(!1,u.ArrayElement);let o=this.pos,n=ht(i,e+1,o),h=i.charCodeAt(n),p=h===61||h===43&&i.charCodeAt(n+1)===61,c=s?.parts,d=this._wordParts;if(p&&(d=At(i,e+1,o,d)??d),!c&&!d)return p?null:At(i,t,o,void 0)??null;let l=i.slice(t,e+1);c||(c=[{type:"Literal",pos:t,end:e+1,text:l,value:s?.value??l}]),d||(d=o>e+1?[{type:"Literal",pos:e+1,end:o,text:i.slice(e+1,o),value:this._wordText}]:[]);let m=is(c,d);return p||(m=At(i,t,o,m)??m),m.length>1||m[0].type!=="Literal"?m:null}buildHereDocParts(t,e){this._buildParts=!0;let i=this.src,s=[],o=!1,n="",h=t,p=t,c=()=>{p>h&&(s.push({type:"Literal",pos:h,end:p,value:n,text:i.slice(h,p)}),n="")};for(;p<e;){let d=i.charCodeAt(p);if(d===92){if(p+1<e){let l=i.charCodeAt(p+1);if(l===10){p+=2;continue}if(l===36||l===96||l===92){n+=String.fromCharCode(l),p+=2;continue}}n+="\\",p++;continue}if(d===36&&i.charCodeAt(p+1)!==39&&i.charCodeAt(p+1)!==34){c(),h=p,this.pos=p,this.readDollar(),this._resultPart?(o=!0,s.push(this._resultPart),h=this.pos):n+=i.slice(p,this.pos),p=this.pos;continue}if(d===96){c(),h=p,this.pos=p,this.readBacktickExpansion(),this._resultPart?(o=!0,s.push(this._resultPart),h=this.pos):n+=i.slice(p,this.pos),p=this.pos;continue}n+=i[p],p++}return c(),o?s:null}registerHereDocTarget(t){if(this.pendingHereDocs!==null){for(let e of this.pendingHereDocs)if(!e.target){e.target=t;return}}}readTestRegexWord(){this.hasPeek=!1,this.skipSpacesAndTabs();let t=this.src,e=this.srcEnd,i=this.pos,s=0;for(;this.pos<e;){let o=t.charCodeAt(this.pos);if(o===40){s++,this.pos++;continue}if(o===92){this.pos+=this.pos+1<e?2:1;continue}if(o===39){let n=this.pos++,h=n>i&&t.charCodeAt(n-1)===36;for(;this.pos<e&&t.charCodeAt(this.pos)!==39;)h&&t.charCodeAt(this.pos)===92&&this.pos+1<e&&this.pos++,this.pos++;this.pos<e?this.pos++:this.errors.push({message:h?"unterminated ANSI-C quote":"unterminated single quote",pos:n});continue}if(o===34){this.pos++,this.readDoubleQuoted();continue}if(o===96){this.readBacktickExpansion();continue}if(s>0){o===41&&s--,this.pos++;continue}if(o===36){this.readDollar();continue}if((o===60||o===62)&&this.pos+1<e&&t.charCodeAt(this.pos+1)===40){let n=this.pos;this.pos+=2,this.extractBalanced(),this._unbalanced&&this.errors.push({message:"unterminated process substitution",pos:n});continue}if(o<128&&M[o]&1&&o!==124)break;this.pos++}return $(this.current,a.Word,t.slice(i,this.pos),i,this.pos),this.current}readCStyleForExprs(){this.hasPeek=!1;let t=this.src,e=this.srcEnd;for(;this.pos<e&&(t.charCodeAt(this.pos)===32||t.charCodeAt(this.pos)===9);)this.pos++;let i=this.pos;this.pos<e&&t.charCodeAt(this.pos)===40&&this.pos++;let s=[this.pos,0,0],o=["","","",0,0,0],n=0,h=1,p=this.pos,c=!1,d=l=>{let m=t.slice(p,l);o[n]=m.trim(),o[3+n]=s[n]+m.length-m.trimStart().length};for(;this.pos<e&&h>0;){let l=t.charCodeAt(this.pos);if(l===40)h++,this.pos++;else if(l===41){if(h--,h===0){for(c||d(this.pos),n<2&&this.errors.push({message:"expected three arithmetic expressions in for header",pos:this.pos}),this.pos++;this.pos<e&&(t.charCodeAt(this.pos)===32||t.charCodeAt(this.pos)===9);)this.pos++;this.pos<e&&t.charCodeAt(this.pos)===41&&this.pos++;break}this.pos++}else if(l===59&&h===1){if(n===2){c||(d(this.pos),this.errors.push({message:"expected three arithmetic expressions in for header",pos:this.pos})),c=!0,this.pos++;continue}d(this.pos),n++,this.pos++,p=this.pos,s[n]=p}else l===36?this.readDollar():l===96?this.readBacktickExpansion():l===92?this.pos+=this.pos+1<e?2:1:l===39?(this.pos++,this.skipSQ()):l===34?(this.pos++,this.skipDQ()):this.pos++}return h>0&&(c||d(this.pos),this.errors.push({message:"unterminated arithmetic for header",pos:i})),o}peek(t=u.Normal){return this.hasPeek||(this.readNext(this.nextState,t),this.hasPeek=!0),this.nextState}peekFollow(t){if(!this.hasPeek){let e=t[this.current.token]?u.CommandStart:u.Normal;this.readNext(this.nextState,e),this.hasPeek=!0}return this.nextState}next(t=u.Normal){if(this.hasPeek){this.hasPeek=!1;let e=this.current;return this.current=this.nextState,this.nextState=e,this.current}return this.readNext(this.current,t),this.current}unshift(t){this.nextState.copyFrom(t),this.hasPeek=!0}readNext(t,e){let i=this.src,s=this.srcEnd,o=this.pos;for(;;){if(o=Ut(i,o,s),e===u.TestMode)for(;o<s&&i.charCodeAt(o)===10;)o=Ut(i,o+1,s);if(o>=s||i.charCodeAt(o)!==35)break;for(;o<s&&i.charCodeAt(o)!==10;)o++}if(this.pos=o,o>=s){this.consumePendingHereDocs(),$(t,a.EOF,"",o,o);return}let n=o,h=i.charCodeAt(o);if(h===10){this.pos++,this.consumePendingHereDocs(),$(t,a.Newline,`
`,n,this.pos);return}if(e===u.TestMode&&(h===60||h===62)&&!(this.pos+1<this.srcEnd&&i.charCodeAt(this.pos+1)===40)){this.pos++,$(t,a.Word,h===60?"<":">",n,this.pos),t.keywordEligible=!0;return}h<128&&M[h]&1&&this.tryReadOperator(t,h,e,n)||h===91&&e===u.ArrayElement&&this.readArrayElement(t,n)||this.readWord(t,e,n)}tryReadOperator(t,e,i,s){let o=this.src,n=this.pos,h=n+1<this.srcEnd?o.charCodeAt(n+1):0;switch(e){case 59:return h===59?n+2<this.srcEnd&&o.charCodeAt(n+2)===38?(this.pos+=3,$(t,a.DoubleSemiAmp,";;&",s,this.pos),!0):(this.pos+=2,$(t,a.DoubleSemi,";;",s,this.pos),!0):h===38?(this.pos+=2,$(t,a.SemiAmp,";&",s,this.pos),!0):(this.pos++,$(t,a.Semi,";",s,this.pos),!0);case 124:return h===124?(this.pos+=2,$(t,a.Or,"||",s,this.pos),!0):h===38?(this.pos+=2,$(t,a.Pipe,"|&",s,this.pos),!0):(this.pos++,$(t,a.Pipe,"|",s,this.pos),!0);case 38:if(h===38)return this.pos+=2,$(t,a.And,"&&",s,this.pos),!0;if(h===62){this.pos+=2;let p=this.pos<this.srcEnd&&o.charCodeAt(this.pos)===62;return p&&this.pos++,this.readRedirectTarget(t,p?"&>>":"&>",s)}return this.pos++,$(t,a.Amp,"&",s,this.pos),!0;case 40:if(i===u.CommandStart&&h===40){let p=this.errors.length;if(this.readArithmeticCommand(t,s),!this._notArithmetic)return!0;this.errors.length=p,this.pos=s}return this.pos++,$(t,a.LParen,"(",s,this.pos),!0;case 41:return this.pos++,$(t,a.RParen,")",s,this.pos),!0;case 60:case 62:return this.readRedirection(t,s);default:return!1}}readRedirection(t,e){let i=this.src,s=i.charCodeAt(this.pos),o="";if(s===60){this.pos++;let n=this.pos<this.srcEnd?i.charCodeAt(this.pos):0;if(n===60){this.pos++;let h=this.pos<this.srcEnd?i.charCodeAt(this.pos):0;if(h===60)this.pos++,o="<<<";else{let p=h===45;p&&this.pos++,this.skipSpacesAndTabs();let c=this.pos;(this.pos>=this.srcEnd||i.charCodeAt(this.pos)!==35)&&this.readHereDocDelimiter();let d=this.pos>c;return d&&(this.pendingHereDocs??(this.pendingHereDocs=[])).push({delimiter:this._hereDelim,strip:p,quoted:this._hereQuoted}),$(t,a.Redirect,p?"<<-":"<<",e,this.pos),t.content=d?this._hereDelim:void 0,t.delimiterQuoted=d&&this._hereQuoted,t.targetPos=c,t.targetEnd=d?this.pos:c,!0}}else{if(n===40)return this.readProcessSubstitution(t,"<",e),!0;n===62?(o="<>",this.pos++):n===38?(o="<&",this.pos++):o="<"}}else if(s===62){this.pos++;let n=this.pos<this.srcEnd?i.charCodeAt(this.pos):0;if(n===40)return this.readProcessSubstitution(t,">",e),!0;n===62?(o=">>",this.pos++):n===38?(o=">&",this.pos++):n===124?(o=">|",this.pos++):o=">"}return this.readRedirectTarget(t,o,e)}readRedirectTarget(t,e,i){let s=this.src;if(this.skipSpacesAndTabs(),this.pos<this.srcEnd){let o=s.charCodeAt(this.pos);if((o===60||o===62)&&this.pos+1<this.srcEnd&&s.charCodeAt(this.pos+1)===40){let h=this.pos;this.pos+=2,this.extractBalanced(),this._unbalanced&&this.errors.push({message:"unterminated process substitution",pos:h});let p=s.slice(h,this.pos);return $(t,a.Redirect,e,i,this.pos),t.content=p,t.targetPos=h,t.targetEnd=this.pos,!0}let n=this.pos;return o!==10&&o!==35&&this.readRedirectTargetText(),this.redirectToken(t,e,i,n),!0}return this.redirectToken(t,e,i,this.pos),!0}readRedirectTargetText(){let t=this._buildValue;this._buildValue=!0,this.readWordText(),this._buildValue=t}redirectToken(t,e,i,s){let o=this.pos>s&&(this._wordText.length>0||this._wordQuoted);$(t,a.Redirect,e,i,this.pos),t.content=o?this._wordText:void 0,t.targetPos=s,t.targetEnd=o?this.pos:s}readProcessSubstitution(t,e,i){this.pos++,this.extractBalanced(),this._unbalanced&&this.errors.push({message:"unterminated process substitution",pos:i});let s=this.src.slice(i,this.pos);$(t,a.Word,s,i,this.pos)}readHereDocDelimiter(){let t=this.src,e=this.srcEnd,i=this._buildValue;this._buildValue=!0;let s="",o=!1;for(;this.pos<e;){let n=t.charCodeAt(this.pos);if(n===39){for(o=!0,this.pos++;this.pos<e&&t.charCodeAt(this.pos)!==39;)s+=t[this.pos],this.pos++;this.pos<e&&this.pos++}else if(n===34){for(o=!0,this.pos++;this.pos<e&&t.charCodeAt(this.pos)!==34;){if(t.charCodeAt(this.pos)===92&&this.pos+1<e){let h=t.charCodeAt(this.pos+1);if(h===10){this.pos+=2;continue}(h===36||h===96||h===34||h===92)&&this.pos++}s+=t[this.pos],this.pos++}this.pos<e&&this.pos++}else if(n===92){if(this.pos+1<e&&t.charCodeAt(this.pos+1)===10){this.pos+=2;continue}o=!0,this.pos++,this.pos<e?(s+=t[this.pos],this.pos++):s+="\\"}else if(n===96){let h=this.pos;for(this.pos++;this.pos<e&&t.charCodeAt(this.pos)!==96;)t.charCodeAt(this.pos)===92&&this.pos++,this.pos++;this.pos<e&&this.pos++,s+=t.slice(h,this.pos)}else if(n===36){let h=this.pos+1<e?t.charCodeAt(this.pos+1):0;(h===39||h===34)&&(o=!0),this.readDollar(),s+=this._resultText}else{if(n<128&&M[n]&1)break;s+=t[this.pos],this.pos++}}this._buildValue=i,this._hereDelim=s,this._hereQuoted=o}consumePendingHereDocs(){let t=this.pendingHereDocs;if(!(t===null||t.length===0)){for(let e of t){let i=this.pos,s=this.skipHereDocBody(e.delimiter,e.strip,this.parenBoundary,e.quoted);e.target&&(e.target.body.pos=i,e.target.body.end=s,e.target.body.text=this.src.slice(i,s),this.hereDocClosingPos!==-1&&(e.target.closing={pos:this.hereDocClosingPos,end:this.hereDocClosingEnd}))}t.length=0}}hereDocClosingPos=-1;hereDocClosingEnd=-1;matchHereDocDelimiter(t,e,i,s){let o=this.src,n=e;for(let h=0;h<t.length;){if(s&&o.charCodeAt(n)===92&&n+1<i&&o.charCodeAt(n+1)===10){n+=2;continue}if(n>=i||o.charCodeAt(n)!==t.charCodeAt(h))return-1;n++,h++}return n}logicalLineEnd(t,e,i){let s=this.src,o=s.indexOf(`
`,t);for(;o!==-1&&o<e;){if(!i)return o;let n=o;for(;n>t&&s.charCodeAt(n-1)===92;)n--;if((o-n)%2===0)return o;t=o+1,o=s.indexOf(`
`,t)}return e}skipHereDocBody(t,e,i=!1,s=!1){this.hereDocClosingPos=-1,this.hereDocClosingEnd=-1;let o=this.src,n=this.srcEnd;for(;this.pos<n;){let h=this.pos,p=this.logicalLineEnd(h,n,!s);for(;;){let d=h;if(e)for(;h<p&&o.charCodeAt(h)===9;)h++;if(s||(h=ht(o,h,p)),h===d)break}let c=this.matchHereDocDelimiter(t,h,p,!s);if(c!==-1&&!s&&(c=ht(o,c,p)),c===p){let d=this.pos;return(!i||!this.parenBoundary||p!==n)&&(this.hereDocClosingPos=h,this.hereDocClosingEnd=p),this.pos=p<n?p+1:p,d}if(i&&c!==-1){let d=c;for(;d<p&&o.charCodeAt(d)!==41;)d++;if(d<p||this.parenBoundary&&p===n){let l=this.pos;return this.pos=c,l}}this.pos=p<n?p+1:p}return this.pos}_wordText="";_wordRaw=!1;_wordQuoted=!1;_wordHasExpansions=!1;_wordKeywordEligible=!1;_wordAssignmentState;_bracketWindow;_arithmeticBracketWindow;_wordParts=null;_resultText="";_resultIsRaw=!0;_resultHasExpansion=!1;_resultPart;_unbalanced=!1;_notArithmetic=!1;_arithUnterminated=!1;_dqText="";_dqHasExpansions=!1;_dqParts=null;_dqEnd=0;_hereDelim="";_hereQuoted=!1;readWord(t,e,i=0){let s=this._errors===null?0:this._errors.length;if(this.readWordText(!1,e,!0),e===u.Normal){let o=this.pos<this.srcEnd?this.src.charCodeAt(this.pos):0;if(!this._wordRaw||o!==60&&o!==62){Dt(t,a.Word,i,this.pos,this._wordRaw),t.keywordEligible=this._wordKeywordEligible;return}}this.classifyWord(t,e,i,s)}isRedirectVariable(t,e){let i=this.src;for(let s=t;s<e;s++){let o=i.charCodeAt(s);if(o===91&&s>t)return s+2<e&&i.charCodeAt(e-1)===93&&this.findClosingShellDelimiter(s+1,e,93)===e-1;if(o>=128||!(Et[o]&(s===t?1:2)))return!1}return e>t}readArrayElement(t,e){let i=this.findClosingBracket(e+1);return i===-1?!1:(this.pos=i+1,this.readWordText(),Dt(t,a.Word,e,this.pos,!0),!0)}classifyWord(t,e,i,s){let o=this.src,n=this._wordRaw,h=this._wordHasExpansions,p=this._wordQuoted,c=this._wordKeywordEligible,d=this._wordAssignmentState,l=this.pos,m=l-i;if(e===u.CasePattern){let k=a.Word;if(c){let x=n?He(o,i,m):be.get(this.wordValueOf(i,l));x===a.Esac&&(k=x)}Dt(t,k,i,l,n),t.keywordEligible=c;return}let A=null;if(!n&&!h&&c&&(e===u.CommandStart||e===u.TestMode)){let k=l<this.srcEnd?o.charCodeAt(l):0;(!p&&m<=16||k===60||k===62)&&(A=this.wordValueOf(i,l))}if(e===u.CommandStart&&c){if(n){if(m<=8){let k=He(o,i,m);if(k!==void 0){Dt(t,k,i,l,!0);return}}if(m===2&&o.charCodeAt(i)===91&&o.charCodeAt(i+1)===91){Dt(t,a.DblLBracket,i,l,!0);return}}else if(A!==null&&A.length>0){let k=A.charCodeAt(0);if(k>=97&&k<=122&&A.length<=8||k===33||k===123||k===125){let x=be.get(A);if(x!==void 0){$(t,x,A,i,l);return}}if(k===91&&A==="[["){$(t,a.DblLBracket,A,i,l);return}}}if(e===u.CommandStart||e===u.CommandPrefix||e===u.Declaration){if(d===void 0){let k=-1,x=!1;for(let f=i+1;f<l;f++){let C=o.charCodeAt(f);if(C===61){k=f;break}C===91&&(x=!0)}(k!==-1||e!==u.Declaration&&x)&&(d=ae(o,i,l,ce))}if(d!==void 0){if(e!==u.Declaration&&d>=it){if(l<this.srcEnd&&(this._errors!==null&&(this._errors.length=s),this.pos=i,this.readWordText(!0,e,!0),this.pos>l)){this.classifyWord(t,e,i,s);return}(this._errors===null||this._errors.length===s)&&this.errors.push({message:"unterminated array subscript",pos:o.indexOf("[",i)})}if(Te(d)){Dt(t,a.Assignment,i,l,n),A!==null&&(t._value=A),t.assignmentOperatorPos=Be(d);return}}}if((e===u.CommandStart||e===u.TestMode)&&c){if(n){if(m===2&&o.charCodeAt(i)===93&&o.charCodeAt(i+1)===93){Dt(t,a.DblRBracket,i,l,!0);return}}else if(A==="]]"){$(t,a.DblRBracket,A,i,l);return}}if(!h&&this.pos<this.srcEnd){let k=o.charCodeAt(this.pos);if((k===60||k===62)&&n){let x=o.charCodeAt(i);if(x>=48&&x<=57&&ss(o,i,l)){let f=Number.parseInt(o.slice(i,l),10);if(this.readRedirection(t,i)){t.fileDescriptor=f,t.descriptorEnd=l;return}}if(x===123&&o.charCodeAt(l-1)===125&&this.isRedirectVariable(i+1,l-1)){let f=o.slice(i+1,l-1);if(this.readRedirection(t,i)){t.variableName=f,t.descriptorEnd=l;return}}}}Dt(t,a.Word,i,l,n),A!==null&&(t._value=A),t.keywordEligible=c}readWordText(t=!1,e=u.Normal,i=!1){let s=this.src,o=this.srcEnd,n=this.pos,h=n,p=0;for(;n<o;){let P=s.charCodeAt(n);if(P<128&&M[P]){p=P;break}n++}if(n>=o||M[p]&1&&!(p===40&&n>h&&bt[s.charCodeAt(n-1)])&&!t){this.pos=n,this._wordText=(this._buildParts||this._buildValue)&&n>h?s.slice(h,n):"",this._wordRaw=!0,this._wordQuoted=!1,this._wordHasExpansions=!1,this._wordKeywordEligible=!0,this._wordAssignmentState=void 0,this._buildParts&&(this._wordParts=St(this._wordText)&&e!==u.ArrayElement?At(s,h,n,void 0)??null:null);return}let c=this._buildParts,d=c||this._buildValue,l=d&&n>h?s.slice(h,n):"",m=!1,A=!1,k=!0,x=!0,f=n>h?s.charCodeAt(n-1):0,C=ae(s,h,n,ce),E,b="",_=0,g=!1,y;for(c&&(E=[],b=l,_=h,g=l.length>0&&St(l));n<o;){let P=s.charCodeAt(n);if(P>=128||!M[P]){let w=n;for(n++;n<o;){let B=s.charCodeAt(n);if(B<128&&M[B])break;n++}if(f=s.charCodeAt(n-1),C=ae(s,w,n,C),d){let B=s.slice(w,n);l+=B,c&&(b+=B,g=g||St(B))}continue}if(M[P]&1){if(t&&C>=it){let w=this.findClosingBracket(n);if(w!==-1){let B=w+1;if(C=C===it+1?de:C-1,f=s.charCodeAt(w),d){let L=s.slice(n,B);l+=L,c&&(b+=L,g=g||St(L))}n=B;continue}}if(P===40&&f<128&&bt[f]){if(e===u.FunctionName||C>=it&&(e===u.CommandStart||e===u.CommandPrefix)||e===u.CommandStart&&f===33&&k&&s.charCodeAt(h)===33&&ht(s,h+1,o)===n)break;let w=f;if(w===61&&i&&!((e===u.CommandStart||e===u.CommandPrefix||e===u.Declaration||e===u.EvalArgument)&&Te(C)&&ht(s,Be(C)+1,o)===n))break;let B=n+1,L=this.findClosingShellDelimiter(B,o,41,w===61);if(L===-1&&!i&&!t&&C>=it||e===u.CommandStart&&w!==61&&L!==-1&&Ut(s,B,L)===L)break;k=!1;let rt=L===-1?o:L;if(n=L===-1?o:L+1,L===-1&&this.errors.push({message:"unterminated extended glob",pos:B-2}),f=s.charCodeAt(n-1),d){let v="("+s.slice(B,n);if(l+=v,c&&w!==61){let z=B-2;for(;s.charCodeAt(z)===10&&s.charCodeAt(z-1)===92;)z-=2;if(b.length>0){let I=b.slice(0,-1);z>_&&E.push({type:"Literal",pos:_,end:z,value:I,text:s.slice(_,z)}),b=""}let D=es[w];E.push({type:"ExtendedGlob",pos:z,end:n,text:s.slice(z,n),operator:D,pattern:s.slice(B,rt),parts:this.subFieldParts(B,rt)}),_=n}else c&&(b+=v,g=g||St(v))}continue}break}if(P===92){n++,n<o?s.charCodeAt(n)===10?(n++,x=!1):(C>=0&&C<it&&(C=ut),m=!0,k=!1,x=!1,f=s.charCodeAt(n),d&&(l+=s[n],c&&(b+=s[n])),n++):(C>=0&&C<it&&(C=ut),m=!0,k=!1,f=92,d&&(l+="\\",c&&(b+="\\")));continue}if(P===39){let w=n;C>=0&&C<it&&(C=ut),m=!0,k=!1,x=!1,n++;let B=n;for(;n<o&&s.charCodeAt(n)!==39;)n++;n>B&&(f=s.charCodeAt(n-1));let L=d?s.slice(B,n):"";d&&(l+=L),n<o?n++:this.errors.push({message:"unterminated single quote",pos:B-1}),c&&(w>_&&(E.push({type:"Literal",pos:_,end:w,value:b,text:s.slice(_,w)}),b=""),E.push({type:"SingleQuoted",pos:w,end:n,value:L,text:s.slice(w,n)}),_=n);continue}if(P===34){let w=n;if(C>=0&&C<it&&(C=ut),m=!0,k=!1,x=!1,n++,this.pos=n,this.readDoubleQuoted(),n=this.pos,this._dqEnd>w+1&&(f=s.charCodeAt(this._dqEnd-1)),this._dqHasExpansions&&(A=!0),d&&(l+=this._dqText),c){w>_&&(E.push({type:"Literal",pos:_,end:w,value:b,text:s.slice(_,w)}),b="");let B=s.slice(w,n);E.push({type:"DoubleQuoted",pos:w,end:n,text:B,parts:this._dqParts??[{type:"Literal",pos:w+1,end:this._dqEnd,value:this._dqText,text:s.slice(w+1,this._dqEnd)}]}),_=n}continue}if(P===36){k=!1;let w=n;C>=0&&C<it&&(C=ut);let B=n+1<o?s.charCodeAt(n+1):0;(B===39||B===34)&&(m=!0),this.pos=n,this.readDollar(),n=this.pos,this._resultIsRaw||(x=!1),n>w&&(f=s.charCodeAt(n-1)),this._resultHasExpansion&&(A=!0),d&&(l+=this._resultText),c&&(this._resultPart?(w>_&&(E.push({type:"Literal",pos:_,end:w,value:b,text:s.slice(_,w)}),b=""),E.push(this._resultPart),_=n):b+=this._resultText);continue}if(P===96){k=!1;let w=n;C>=0&&C<it&&(C=ut),this.pos=n,this.readBacktickExpansion(),n=this.pos,x=!1,f=s.charCodeAt(n-1),A=!0,d&&(l+=this._resultText),c&&(w>_&&(E.push({type:"Literal",pos:_,end:w,value:b,text:s.slice(_,w)}),b=""),E.push(this._resultPart),_=n);continue}if(P===123){C>=0&&C<it&&(C=ut);let w=n+1<o?s.charCodeAt(n+1):0,B=w<=32||w===125?-1:this.scanBraceExpansion(n,y??(y=new Map));if(B>0){if(k=!1,f=s.charCodeAt(B-1),d){let L=s.slice(n,B);l+=L,c&&(n>_&&(E.push({type:"Literal",pos:_,end:n,value:b,text:s.slice(_,n)}),b=""),E.push({type:"BraceExpansion",pos:n,end:B,text:L,parts:this.subFieldParts(n+1,B-1)}),_=B)}n=B;continue}f=123,d&&(l+="{",c&&(b+="{")),n++;continue}n++}c&&n>_&&E.push({type:"Literal",pos:_,end:n,value:b,text:s.slice(_,n)}),this.pos=n,this._wordText=l,this._wordRaw=x,this._wordQuoted=m,this._wordHasExpansions=A,this._wordKeywordEligible=k,this._wordAssignmentState=C,c&&(this._wordParts=E.length>1||E.length===1&&E[0].type!=="Literal"?E:null,g&&e!==u.ArrayElement&&(this._wordParts=At(s,h,n,this._wordParts)??null))}readInnerWordText(t=!1,e=!1,i=!1){let s=this.src,o=this.srcEnd,n=this.pos,h=n,p="",c=this._buildParts,d,l="",m=0,A=!1,k=!1;for(c&&(d=[],m=n);n<o;){let x=s.charCodeAt(n);if(x===92){if(n++,n<o)if(s.charCodeAt(n)===10)n++;else{let f=s.charCodeAt(n);t&&f!==36&&f!==96&&f!==34&&f!==92&&f!==125&&(p+="\\",c&&(l+="\\"),A=!0);let C=s[n++];p+=C,c&&(l+=C)}continue}if(x===39&&!t){let f=n;n++;let C=n;for(;n<o&&s.charCodeAt(n)!==39;)n++;let E=s.slice(C,n);p+=E,n<o&&n++,c&&(f>m&&(d.push({type:"Literal",pos:m,end:f,value:l,text:s.slice(m,f)}),l=""),d.push({type:"SingleQuoted",pos:f,end:n,value:E,text:s.slice(f,n)}),m=n);continue}if(x===34){let f=n;if(n++,this.pos=n,this.readDoubleQuoted(t),n=this.pos,p+=this._dqText,c){f>m&&(d.push({type:"Literal",pos:m,end:f,value:l,text:s.slice(m,f)}),l="");let C=s.slice(f,n);d.push({type:"DoubleQuoted",pos:f,end:n,text:C,parts:this._dqParts??[{type:"Literal",pos:f+1,end:this._dqEnd,value:this._dqText,text:s.slice(f+1,this._dqEnd)}]}),m=n}continue}if(x===36){let f=n;this.pos=n,this.readDollar(t),n=this.pos,p+=this._resultText,c&&(this._resultPart?(f>m&&(d.push({type:"Literal",pos:m,end:f,value:l,text:s.slice(m,f)}),l=""),d.push(this._resultPart),m=n):l+=this._resultText);continue}if(x===96){let f=n;this.pos=n,this.readBacktickExpansion(t),n=this.pos,p+=this._resultText,c&&(f>m&&(d.push({type:"Literal",pos:m,end:f,value:l,text:s.slice(m,f)}),l=""),d.push(this._resultPart),m=n);continue}if(!t&&!e&&(x===60||x===62)&&n+1<o&&s.charCodeAt(n+1)===40){let f=n;this.pos=n+2;let C=this.extractBalanced();n=this.pos;let E=s.slice(f,n);p+=E,c&&(f>m&&(d.push({type:"Literal",pos:m,end:f,value:l,text:s.slice(m,f)}),l=""),d.push(this.processSubstitution(f,E,x===60?"<":">",C)),m=n);continue}p+=s[n],c&&(l+=s[n],i&&(x===42||x===63||x===91)&&(k=!0)),n++}c&&n>m&&d.push({type:"Literal",pos:m,end:n,value:l,text:s.slice(m,n)}),this.pos=n,this._wordText=p,this._wordRaw=!1,this._wordQuoted=!1,this._wordHasExpansions=!1,this._wordKeywordEligible=!1,c&&(this._wordParts=d.length>1||d.length===1&&(d[0].type!=="Literal"||A)?d:null,k&&(this._wordParts=At(s,h,n,this._wordParts)??null))}subFieldParts(t,e){if(!(this._nestingDepth>=256))return It(this.src,t,e)?this.parseSubFieldWord(t,e).parts:At(this.src,t,e,void 0)}parseSubFieldWord(t,e,i="word",s=!1){if(t>=e)return new Z("",t,t);if(this._nestingDepth>=256)return new Z(this.src.slice(t,e),t,e);this._nestingDepth++;let o=this.srcEnd,n=this._bracketWindow,h=this._arithmeticBracketWindow,p=this.pos,c=this._wordText,d=this._wordParts,l=this._wordQuoted,m=this._wordKeywordEligible;this.srcEnd=e,this.pos=t,this.readInnerWordText(i==="quoted",s,i==="word");let A=new Z(this.src.slice(t,e),t,e);return this._buildParts&&(A.parts=this._wordParts??void 0),this.srcEnd=o,this._bracketWindow=n,this._arithmeticBracketWindow=h,this.pos=p,this._wordText=c,this._wordParts=d,this._wordQuoted=l,this._wordKeywordEligible=m,this._nestingDepth--,A}skipSQ(){for(;this.pos<this.srcEnd&&this.src.charCodeAt(this.pos)!==39;)this.pos++;this.pos<this.srcEnd&&this.pos++}skipAnsiCQuoted(){let t=this.pos-1,e=Qt(this.src,this.pos,this.srcEnd);this.pos=e.end,e.closed||this.errors.push({message:"unterminated ANSI-C quote",pos:t})}skipDQ(){let t=this.src,e=this.srcEnd;if(this._unbalanced=!1,this._nestingDepth<256)for(;this.pos<e;){let i=t.charCodeAt(this.pos);if(i===34){this.pos++;return}if(i===92){this.pos=Math.min(this.pos+2,e);continue}if(i===96)break;if(i===36){let s=t.charCodeAt(this.pos+1);if(s===40||s===123||s===36)break}this.pos++}this.extractBalanced(!0,!0)}skipSpacesAndTabs(){this.pos=Ut(this.src,this.pos,this.srcEnd)}readDoubleQuoted(t=!1){let e=this.src,i=this.srcEnd,s=this.pos,o=!1,n=this._buildParts,h=n||this._buildValue;if(!n){let m=this.pos;for(;m<i;){let A=e.charCodeAt(m);if(A===34){this._dqText=h?e.slice(s,m):"",this._dqEnd=m,this.pos=m+1,this._dqHasExpansions=!1,this._dqParts=null;return}if(A===36||A===96||A===92)break;m++}}let p="",c=null,d="",l=n?this.pos:0;for(;this.pos<i&&e.charCodeAt(this.pos)!==34;){let m=this.pos;for(;this.pos<i;){let k=e.charCodeAt(this.pos);if(k===34||k===92||k===36||k===96)break;this.pos++}if(h&&this.pos>m){let k=e.slice(m,this.pos);p+=k,n&&(d+=k)}if(this.pos>=i||e.charCodeAt(this.pos)===34)break;let A=e.charCodeAt(this.pos);if(A===92){if(this.pos++,this.pos<i){let k=e.charCodeAt(this.pos);if(k===10){this.pos++;continue}if(h)if(t||k===36||k===96||k===34||k===92){let x=e[this.pos];p+=x,n&&(d+=x)}else{let x="\\"+e[this.pos];p+=x,n&&(d+=x)}this.pos++}continue}if(A===36){let k=this.pos+1<i?e.charCodeAt(this.pos+1):0;if(k===34||k===39){h&&(p+="$",n&&(d+="$")),this.pos++;continue}let x=this.pos;if(this.readDollar(!0),h&&(p+=this._resultText),this._resultHasExpansion&&(o=!0),n){let f=this._resultPart;f&&Pe(f)?(c||(c=[]),x>l&&(c.push({type:"Literal",pos:l,end:x,value:d,text:e.slice(l,x)}),d=""),c.push(f),l=this.pos):d+=this._resultText}continue}if(A===96){let k=this.pos;this.readBacktickExpansion(!0),h&&(p+=this._resultText),o=!0,n&&this._resultPart&&Pe(this._resultPart)&&(c||(c=[]),k>l&&(c.push({type:"Literal",pos:l,end:k,value:d,text:e.slice(l,k)}),d=""),c.push(this._resultPart),l=this.pos);continue}}n&&c&&this.pos>l&&c.push({type:"Literal",pos:l,end:this.pos,value:d,text:e.slice(l,this.pos)}),this._dqEnd=this.pos,this.pos<i?this.pos++:this.errors.push({message:"unterminated double quote",pos:s-1}),this._dqText=p,this._dqHasExpansions=o,this._dqParts=c}readDollar(t=!1){let e=this.pos;this.pos++;let i=this.src,s=this.srcEnd,o=this._buildParts||this._buildValue;if(this.pos>=s){this._resultText="$",this._resultIsRaw=!0,this._resultHasExpansion=!1,this._resultPart=void 0;return}let n=ht(i,this.pos,s);n>this.pos&&i.charCodeAt(n)===40&&i.charCodeAt(n+1)!==40&&(this.pos=n);let h=i.charCodeAt(this.pos);if(h===40){if(this.pos+1<s&&i.charCodeAt(this.pos+1)===40){let p=this.pos,c=this.errors.length;if(this.readArithmeticExpansion(e),!this._notArithmetic)return;this.errors.length=c,this.pos=p}this.readCommandSubstitution(e);return}if(h===123){let p=this.pos+1<s?i.charCodeAt(this.pos+1):0;if(p===32||p===9||p===10){this.readBraceCommandSubstitution();return}if(p===124){this.readValueSubstitution();return}this.readParameterExpansion(t);return}if(h===39){if(this.pos++,o){let p=this.readAnsiCQuoted();this._resultText=p,this._resultPart=this._buildParts?{type:"AnsiCQuoted",pos:e,end:this.pos,text:i.slice(e,this.pos),value:p}:void 0}else this.skipAnsiCQuoted(),this._resultText="",this._resultPart=void 0;this._resultIsRaw=!1,this._resultHasExpansion=!1;return}if(h===34){if(this.pos++,this.readDoubleQuoted(t),this._resultText=this._dqText,this._resultIsRaw=!1,this._resultHasExpansion=this._dqHasExpansions,this._buildParts){let p=i.slice(e,this.pos);this._resultPart={type:"LocaleString",pos:e,end:this.pos,text:p,parts:this._dqParts??[{type:"Literal",pos:e+2,end:this._dqEnd,value:this._dqText,text:i.slice(e+2,this._dqEnd)}]}}else this._resultPart=void 0;return}if(h===64||h===42||h===35||h===63||h===45||h===36||h===33||h>=48&&h<=57){this.pos++;let p=o?i.slice(this.pos-2,this.pos):"";this._resultText=p,this._resultIsRaw=!0,this._resultHasExpansion=!1,this._resultPart=this._buildParts?{type:"SimpleExpansion",pos:e,end:this.pos,text:p}:void 0;return}if(h<128&&Et[h]&1){let p=this.pos-1;for(;this.pos<s;){let d=i.charCodeAt(this.pos);if(d<128&&Et[d]&2)this.pos++;else break}let c=o?i.slice(p,this.pos):"";this._resultText=c,this._resultIsRaw=!0,this._resultHasExpansion=!1,this._resultPart=this._buildParts?{type:"SimpleExpansion",pos:e,end:this.pos,text:c}:void 0;return}if(h===91){let p=this._errors===null?0:this._errors.length,c=this.findClosingArithmeticBracket(this.pos+1);if(c!==-1){let d=this.pos+1,l=i.slice(d,c);this.pos=c+1;let m=o?i.slice(e,this.pos):"";this._resultText=m,this._resultIsRaw=!0,this._resultHasExpansion=!1,this._resultPart=this._buildParts?this.arithmeticExpansion(d-2,m,l,d):void 0;return}this._errors!==null&&(this._errors.length=p)}this._resultText="$",this._resultIsRaw=!0,this._resultHasExpansion=!1,this._resultPart=void 0}scanArithmeticBody(){this._notArithmetic=!1,this._arithUnterminated=!1,this.pos+=2;let t=1,e=0,i=0,s,o=!1,n=this.src,h=this.srcEnd,p=this.pos;for(;this.pos<h&&t>0;){let d=n.charCodeAt(this.pos);if(d===92)this.pos+=this.pos+1<h?2:1;else if(d===39)this.pos++,this.skipSQ();else if(d===34)this.pos++,this.skipDQ();else if(d===96){for(this.pos++;this.pos<h&&n.charCodeAt(this.pos)!==96;)n.charCodeAt(this.pos)===92&&this.pos++,this.pos++;this.pos<h&&this.pos++}else if(d===36&&this.pos+2<h&&n.charCodeAt(this.pos+1)===40&&n.charCodeAt(this.pos+2)!==40){let l=this.pos;this.pos+=2,this.extractBalanced(),this._unbalanced&&this.errors.push({message:"unterminated command substitution",pos:l})}else if(d===36&&this.pos+1<h&&n.charCodeAt(this.pos+1)===123){let l=this.findClosingBrace(this.pos+2,h);this.pos=l===-1?h:l+1}else if((d===60||d===62)&&this.pos+1<h&&n.charCodeAt(this.pos+1)===40)this.pos+=2,this.extractBalanced();else if(d===40)n.charCodeAt(this.pos-1)===36&&n.charCodeAt(this.pos+1)===40?(t===1?i=e:(s??(s=[])).push(e),t++,e=0,t+this._nestingDepth>256&&!o&&(this.errors.push({message:"maximum arithmetic expansion nesting depth exceeded",pos:this.pos-1}),o=!0),this.pos+=2):(++e+t-1+this._nestingDepth>256&&!o&&(this.errors.push({message:"maximum arithmetic expression nesting depth exceeded",pos:this.pos}),o=!0),this.pos++);else if(d===41&&e>0)e--,this.pos++;else if(d===41&&this.pos+1<h&&n.charCodeAt(this.pos+1)===41){if(--t===0){this.pos+=2;break}e=t===1?i:s.pop(),this.pos+=2}else{if(d===41&&t===1)return this._notArithmetic=!0,"";this.pos++}}let c=this._buildParts||this._buildValue;return t>0?(this._arithUnterminated=!0,c?n.slice(p,this.pos):""):c?n.slice(p,this.pos-2):""}readArithmeticExpansion(t){let e=this.pos+2,i=this.scanArithmeticBody();if(this._notArithmetic)return;this._arithUnterminated&&this.errors.push({message:"unterminated arithmetic expansion",pos:t});let s=this._buildParts||this._buildValue?this.src.slice(t,this.pos):"";this._resultText=s,this._resultIsRaw=!0,this._resultHasExpansion=!1,this._resultPart=this._buildParts?this.arithmeticExpansion(t,s,i,e):void 0}arithmeticExpansion(t,e,i,s){return new Nt(t,this.pos,e,{source:this.src,body:i,offset:s,depth:this._nestingDepth,context:this.substitutionContext})}readArithmeticCommand(t,e){let i=this._buildValue;this._buildValue=!0;let s=this.scanArithmeticBody();this._buildValue=i,this._arithUnterminated&&this.errors.push({message:"unterminated arithmetic command",pos:e}),$(t,a.ArithCmd,s,e,this.pos)}readCommandSubstitution(t){let e=this.pos;this.pos++;let i=this.extractBalanced();this._unbalanced&&this.errors.push({message:"unterminated command substitution",pos:t});let s=this._buildParts||this._buildValue,o=s?this.src.slice(t,this.pos):"",n=!s||e===t+1?o:"$"+this.src.slice(e,this.pos);this._resultText=n,this._resultIsRaw=e===t+1,this._resultHasExpansion=!0,this._buildParts?this._resultPart=this.commandExpansion(t,o,e+1,i,!this._unbalanced):this._resultPart=void 0}readBraceCommandSubstitution(){this.readBraceSubstitution(1)}readValueSubstitution(){this.readBraceSubstitution(2)}readBraceSubstitution(t){let e=this.pos-1;this.pos+=t;let i=this.src,s=this.srcEnd,o=1,n=this.pos;for(;this.pos<s;){let h=i.charCodeAt(this.pos);if(h===123)o++;else if(h===125){if(--o===0){this.pos++;break}}else if(h===39){this.pos++,this.skipSQ();continue}else if(h===34){this.pos++,this.skipDQ();continue}else h===92&&this.pos++;this.pos++}if(this._resultIsRaw=!0,this._resultHasExpansion=!0,this._buildParts||this._buildValue){let h=i.slice(n,this.pos-1),p=h.trim(),c=i.slice(e,this.pos);if(this._resultText=c,this._buildParts){let d=n+(h.length-h.trimStart().length);this._resultPart=this.commandExpansion(e,c,d,p)}else this._resultPart=void 0}else this._resultText="",this._resultPart=void 0}readBacktickExpansion(t=!1){this.pos++;let e=this.src,i=this.srcEnd,s=this.pos;if(!this._buildParts&&!this._buildValue){for(;this.pos<i&&e.charCodeAt(this.pos)!==96;)e.charCodeAt(this.pos)===92&&this.pos+1<i&&this.pos++,this.pos++;this.pos<i?this.pos++:this.errors.push({message:"unterminated backtick",pos:s-1}),this._resultText="",this._resultIsRaw=!1,this._resultHasExpansion=!0,this._resultPart=void 0;return}let o="",n=!1;for(;this.pos<i&&e.charCodeAt(this.pos)!==96;){if(e.charCodeAt(this.pos)===92){n=!0;break}this.pos++}if(!n)o=e.slice(s,this.pos);else for(o=e.slice(s,this.pos);this.pos<i&&e.charCodeAt(this.pos)!==96;)if(e.charCodeAt(this.pos)===92){if(this.pos++,this.pos<i){let p=e.charCodeAt(this.pos);p===36||p===96||p===92||t&&p===34?o+=e[this.pos]:o+="\\"+e[this.pos],this.pos++}}else{let p=this.pos;for(;this.pos<i;){let c=e.charCodeAt(this.pos);if(c===96||c===92)break;this.pos++}o+=e.slice(p,this.pos)}this.pos<i?this.pos++:this.errors.push({message:"unterminated backtick",pos:s-1});let h=e.slice(s-1,this.pos);this._resultText=o,this._resultHasExpansion=!0,this._buildParts?this._resultPart=n?this.decodedCommandExpansion(s-1,h,o):this.commandExpansion(s-1,h,s,o):this._resultPart=void 0}readParameterExpansion(t){let e=this.src,i=this.srcEnd,s=this.pos;this.pos++;let o=1,n=!1;for(;this.pos<i&&o>0;){let c=e.charCodeAt(this.pos);if(c===36){let d=this.pos+1<i?e.charCodeAt(this.pos+1):0;if(d===123){o++,this._nestingDepth+o>256&&!n&&(this.errors.push({message:"maximum parameter expansion nesting depth exceeded",pos:this.pos}),n=!0),this.pos+=2;continue}if(d===36){this.pos+=2;continue}if(d===40){let l=this.pos;this.pos+=2,this.extractBalanced(),this._unbalanced&&this.errors.push({message:"unterminated command substitution",pos:l});continue}}else if(c===96){for(this.pos++;this.pos<i&&e.charCodeAt(this.pos)!==96;)e.charCodeAt(this.pos)===92&&this.pos++,this.pos++;this.pos<i&&this.pos++;continue}else if(c===125){if(--o===0){this.pos++;break}}else if(c===92)this.pos+1<i&&this.pos++;else if(c===39){this.pos++,this.pos>s+1&&e.charCodeAt(this.pos-2)===36?this.skipAnsiCQuoted():this.skipSQ();continue}else if(c===34){this.pos++,this.skipDQ();continue}this.pos++}let h=o===0;h||this.errors.push({message:"unterminated parameter expansion",pos:s-1});let p=this._buildParts||this._buildValue?e.slice(s-1,this.pos):"";if(this._resultText=p,this._resultIsRaw=!0,this._resultHasExpansion=!1,this._buildParts){let c=e.slice(s+1,h?this.pos-1:this.pos);this._resultPart=this.parseParamInner(p,c,s+1,t)}else this._resultPart=void 0}parseParamInner(t,e,i,s){let o={type:"ParameterExpansion",pos:i-2,end:this.pos,text:t,parameter:"",parameterPos:i,parameterEnd:i,prefix:void 0,index:void 0,operation:void 0},n=e.length;if(n===0)return o;let h=(f,C,E="word")=>this.parseSubFieldWord(i+f,i+C,E),p=f=>{let C=this.findClosingBracket(i+f,i+n);return C===-1?-1:C-i},c=0;if(n>1&&e.charCodeAt(0)===33&&(o.prefix="!",c=1),n>1&&e.charCodeAt(0)===35){let f=this.scanParamName(e,1),C=f,E=-1;if(C<n&&e.charCodeAt(C)===91&&(E=p(C+1),E!==-1&&(C=E+1)),f>1&&C===n)return o.prefix="#",o.parameter=e.slice(1,f),o.parameterPos=i+1,o.parameterEnd=i+f,E!==-1&&(o.index=h(f+1,E,"embedded")),o}let d=c;if(c=this.scanParamName(e,c),o.parameter=e.slice(d,c),o.parameterPos=i+d,o.parameterEnd=i+c,c<n&&e.charCodeAt(c)===91){let f=p(c+1);f!==-1&&(o.index=h(c+1,f,"embedded"),c=f+1)}if(c>=n)return o;let l=i+c,m=i+n,A=e.charCodeAt(c),k=e.charCodeAt(c+1),x=e[A===58?c+1:c];if(x==="-"||x==="="||x==="+"||x==="?"){let f=c+(A===58?2:1);return o.operation={type:"Default",pos:l,end:m,operatorEnd:i+f,operator:A===58?`:${x}`:x,operand:h(f,n,s?"quoted":"word")},o}if(A===58){c++;let f=i+c,C=we(e.slice(c),58,!0,(E,b)=>this.findNestedShellEnd(f+E,m,b)-f);return o.operation={type:"Slice",pos:l,end:m,operatorEnd:f,operator:":",offset:h(c,C===-1?n:c+C,"embedded"),length:C===-1?void 0:h(c+C+1,n,"embedded")},o}if(A===35||A===37){let f=k===A,C=A===35?f?"##":"#":f?"%%":"%",E=c+(f?2:1);return o.operation={type:"Remove",pos:l,end:m,operatorEnd:i+E,operator:C,operand:h(E,n)},o}if(A===47){c++;let f="/";(k===47||k===35||k===37)&&(f=k===47?"//":k===35?"/#":"/%",c++);let C=c;k===47&&(C=ht(e,c,n),e.charCodeAt(C)===47&&C++);let E=we(e.slice(C),47),b=E===-1?n:C+E;return o.operation={type:"Replace",pos:l,end:m,operatorEnd:i+c,operator:f,pattern:h(c,b),replacement:h(E===-1?n:b+1,n)},o}if(A===94||A===44){let f=k===A,C=A===94?f?"^^":"^":f?",,":",",E=c+(f?2:1);return o.operation={type:"CaseModification",pos:l,end:m,operatorEnd:i+E,operator:C,operand:E===n?void 0:h(E,n)},o}return o.prefix==="!"&&o.index===void 0&&c+1===n&&(A===64||A===42)?(o.operation={type:"Names",pos:l,end:m,operatorEnd:m,operator:A===64?"@":"*"},o):A===64?(o.operation={type:"Transform",pos:l,end:m,operatorEnd:l+1,operator:"@",operand:h(c+1,n)},o):(o.operation={type:"Unknown",pos:l,end:m,operatorEnd:m,operator:e.slice(c)},o)}findNestedShellEnd(t,e,i){let s=this.src.charCodeAt(t);if(s===96){let n=t+1;for(;n<e;){let h=this.src.charCodeAt(n);if(h===92){n+=2;continue}if(n++,h===96)return n}return e}let o;if(s===36){let n=t+1;if(n<e&&this.src.charCodeAt(n)===123)o=this.findClosingBrace(n+1,e);else{let h=ht(this.src,n,e);if(h>=e||this.src.charCodeAt(h)!==40)return t;o=this.findClosingParenthesis(h+1,e)}}else{let n=t+1;if(i||s!==60&&s!==62||n>=e||this.src.charCodeAt(n)!==40)return t;o=this.findClosingParenthesis(n+1,e)}return o===-1?e:o+1}scanParamName(t,e){let i=e;if(i>=t.length)return i;let s=t.charCodeAt(i);if(s===64||s===42||s===35||s===63||s===45||s===36||s===33)return i+1;if(s>=48&&s<=57){for(;i<t.length&&t.charCodeAt(i)>=48&&t.charCodeAt(i)<=57;)i++;return i}if(s>=97&&s<=122||s>=65&&s<=90||s===95)for(i++;i<t.length;){let o=t.charCodeAt(i);if(o>=97&&o<=122||o>=65&&o<=90||o>=48&&o<=57||o===95)i++;else break}return i}readAnsiCQuoted(){let t=this.pos-1,e=Qt(this.src,this.pos,this.srcEnd);return this.pos=e.end,e.closed||this.errors.push({message:"unterminated ANSI-C quote",pos:t}),e.value}extractBalanced(t=!0,e=!1){let i=this.src,s=this.srcEnd,o=this._buildParts||this._buildValue,n=e?0:1,h=this.pos;this._unbalanced=!1;let p=t&&!e?this.substitutionContext.boundaries?.get(h):void 0;if(p!==void 0&&p<=s&&this._nestingDepth<256)return this.pos=p,o?i.slice(h,p-1):"";let c=!0;for(;!e&&this.pos<s;){let v=i.charCodeAt(this.pos);if(v===41){let z=o?i.slice(h,this.pos):"";return this.pos++,z}else{if(v===40||v===92||v===39||v===34||v===96||v===36&&(i.charCodeAt(this.pos+1)===39||i.charCodeAt(this.pos+1)===36||i.charCodeAt(this.pos+1)===123))break;if(v===60&&this.pos+1<s&&i.charCodeAt(this.pos+1)===60)break;if(v===35&&c)break;if(v===99&&c&&this.pos+3<s&&i.charCodeAt(this.pos+1)===97&&i.charCodeAt(this.pos+2)===115&&i.charCodeAt(this.pos+3)===101&&(this.pos+4>=s||i.charCodeAt(this.pos+4)<128&&M[i.charCodeAt(this.pos+4)]&1))break;c=v<128&&(M[v]&1)!==0,this.pos++}}let d=0,l=0,m=[],A=null,k,x=-1,f=h>=2&&i.charCodeAt(h)===40&&i.charCodeAt(h-1)===40&&i.charCodeAt(h-2)===36,C=0,E=!1,b=e?[{close:34,depth:0}]:[],_=e?1:0,g=0,y,P=-1,w=-1,B=-1,L=-1,rt=!1;for(;this.pos<s&&(n>0||b.length>0);){let v=i.charCodeAt(this.pos),z=b.length===0?void 0:b[b.length-1];if(z!==void 0&&z.depth===n){let D=!1;if(v===92){this.pos=Math.min(this.pos+2,s);continue}if(v===z.close){if(b.pop(),v===34?_--:g--,this.pos++,e&&n===0&&b.length===0)return"";continue}if(v===34)b.push({close:34,depth:n}),_++;else if(v===39&&z.close===125){this.pos++,this.skipSQ();continue}else if(v===96){for(this.pos++;this.pos<s&&i.charCodeAt(this.pos)!==96;)i.charCodeAt(this.pos)===92&&this.pos++,this.pos<s&&this.pos++;this.pos<s&&this.pos++;continue}else if(v===36){let I=i.charCodeAt(this.pos+1);if(I===39&&z.close===125){this.pos+=2,this.skipAnsiCQuoted();continue}if(I===123)b.push({close:125,depth:n}),g++,this.pos++;else if(I===40)this.pos++,v=40,D=!0;else if(I===36){this.pos+=i.charCodeAt(this.pos+2)===40?1:2;continue}}if(!E&&this._nestingDepth+Math.max(_,g)>256&&(this.errors.push({message:"maximum word nesting depth exceeded",pos:this.pos}),E=!0),!D){for(this.pos++;this.pos<s;){let I=i.charCodeAt(this.pos);if(I===92||I===34||I===36||I===96||I===125||I===39)break;this.pos++}continue}}if(v===35&&L!==-1&&!f&&x<0&&t&&!rt&&this._nestingDepth+(k?.length??0)<256&&Ut(i,L,s)===this.pos){rt=!0;let D=k?.[k.length-1],I=this.findSubstitutionEnd(D?.start??h,s,this._nestingDepth+(k?.length??0)+1);if(I!==-1){if(D===void 0)return this.pos=I,o?i.slice(h,I-1):"";this.pos=I-1,n=D.depth+1,d=0,l=0,m.length=D.parens+1,v=41}}if(v===40){let D=this.pos>h&&this.pos!==B?i.charCodeAt(this.pos-1):0,I=D===36?this.pos-1:this.pos===w?P:-1,zt=I!==-1||D===60||D===62||D===61||D===64||D===63||D===42||D===43||D===33||D===40&&m[m.length-1]===!1||f&&this.pos===h,ne=I!==-1&&i.charCodeAt(this.pos+1)!==40||(D===60||D===62)&&x<0&&!f;ne&&((k??(k=[])).push({start:this.pos+1,dollar:I,depth:n,parens:m.length,caseDepth:d,caseParens:l,arithBase:x,arithExtent:f,substitutions:C,checkedGrammar:rt,pendingDelims:A}),d=0,l=0,x=-1,f=!1,rt=!1,A=null),m.push(!zt),c=!0,!ne&&x<0&&this.pos+1<s&&i.charCodeAt(this.pos+1)===40&&(x=n),I!==-1&&(ne?C++:(y??(y=[])).push(n),C+(y?.length??0)+this._nestingDepth+(e?0:1)>256&&!E&&(this.errors.push({message:"maximum command substitution nesting depth exceeded",pos:I}),E=!0)),P=-1,w=-1,n++,d>0&&l++,this.pos++}else if(v===41){if(d>0&&l===0)this.pos++,c=!0;else{if(d>0&&l--,n--,n===0&&!e){let I=o?i.slice(h,this.pos):"";return this.pos++,I}if(n<=x&&(x=-1),y!==void 0)for(;y.length>0&&y[y.length-1]>=n;)y.pop();c=m.pop()??!0,this.pos++;let D=k?.[k.length-1];D!==void 0&&D.depth===n&&(k.pop(),d=D.caseDepth,l=D.caseParens,x=D.arithBase,f=D.arithExtent,C=D.substitutions,rt=D.checkedGrammar,A=D.pendingDelims)}L=this.pos}else if(v===92){if(this.pos>h&&i.charCodeAt(this.pos-1)===36){let D=ht(i,this.pos,s);D>this.pos&&i.charCodeAt(D)===40&&i.charCodeAt(D+1)!==40&&(P=this.pos-1,w=D)}this.pos++,this.pos<s&&(i.charCodeAt(this.pos)!==10&&(c=!1),this.pos++),B=this.pos}else if(v===36){let D=i.charCodeAt(++this.pos);D===39?(this.pos++,this.skipAnsiCQuoted()):D===36?this.pos++:D===123&&!ns(i.charCodeAt(this.pos+1))&&(b.push({close:125,depth:n}),g++,this.pos++),c=!1}else if(v===39)this.pos++,this.skipSQ(),c=!1;else if(v===34)b.push({close:34,depth:n}),_++,this.pos++,c=!1;else if(v===96){for(this.pos++;this.pos<s&&i.charCodeAt(this.pos)!==96;)i.charCodeAt(this.pos)===92&&this.pos++,this.pos<s&&this.pos++;this.pos<s&&this.pos++,c=!1}else if(v===60&&x<0&&this.pos+1<s&&i.charCodeAt(this.pos+1)===60){if(this.pos+2<s&&i.charCodeAt(this.pos+2)===60)this.pos+=3;else{this.pos+=2;let D=this.pos<s&&i.charCodeAt(this.pos)===45;D&&this.pos++,this.skipSpacesAndTabs(),this.readHereDocDelimiter(),(this._hereDelim||this._hereQuoted)&&(A??(A=[])).push({delimiter:this._hereDelim,strip:D,quoted:this._hereQuoted})}c=!1}else if(v===10&&A){this.pos++;for(let D of A)this.skipHereDocBody(D.delimiter,D.strip,!0,D.quoted);A=null,c=!0}else if(v===35&&x<0&&!f&&c)for(;this.pos<s&&i.charCodeAt(this.pos)!==10;)this.pos++;else{let D=this.pos;for(;this.pos<s;){let I=i.charCodeAt(this.pos);if(I<128&&M[I])break;this.pos++}if(this.pos>D){if(this.pos-D===4&&c&&x<0&&!f){let zt=i.charCodeAt(D);zt===99&&i.charCodeAt(D+1)===97&&i.charCodeAt(D+2)===115&&i.charCodeAt(D+3)===101?d++:zt===101&&i.charCodeAt(D+1)===115&&i.charCodeAt(D+2)===97&&i.charCodeAt(D+3)===99&&d>0&&(d--,d===0&&(l=0))}c=!1}else{let I=i.charCodeAt(this.pos);c=I<128&&(M[I]&1)!==0,this.pos++}}}return this._unbalanced=!0,e&&k!==void 0&&k.length>0&&this.errors.push({message:"unterminated command substitution",pos:k[0].dollar}),o?i.slice(h,this.pos):""}};function pe(r){switch(r){case",":return 1;case"=":case"+=":case"-=":case"*=":case"/=":case"%=":case"<<=":case">>=":case"&=":case"|=":case"^=":return 2;case"||":return 4;case"&&":return 5;case"|":return 6;case"^":return 7;case"&":return 8;case"==":case"!=":return 9;case"<":case"<=":case">":case">=":return 10;case"<<":case">>":return 11;case"+":case"-":return 12;case"*":case"/":case"%":return 13;case"**":return 14;case"?":return 3;default:return-1}}function We(r,t,e){for(;e>t;){let i=r.charCodeAt(e-1);if(i!==32&&i!==9&&i!==10)break;e--}return e}function os(r){switch(r){case"=":case"+=":case"-=":case"*=":case"/=":case"%=":case"<<=":case">>=":case"&=":case"|=":case"^=":case"**":return!0;default:return!1}}function le(r,t=0,e,i=0){let s=0,o=r.length,n=e?.embeddedWords.length??0;function h(_,g,y=!1){let P={type:"ArithmeticWord",pos:_+t,end:g+t,value:r.slice(_,g),parts:void 0};return y&&e?.embeddedWords.push(P),P}function p(){for(;s<o;){let _=r.charCodeAt(s);if(_===32||_===9||_===10)s++;else break}}function c(){if(s>=o)return null;let _=r.charCodeAt(s),g=s+1<o?r.charCodeAt(s+1):0,y=s+2<o?r.charCodeAt(s+2):0;switch(_){case 44:return s++,",";case 61:return g===61?(s+=2,"=="):(s++,"=");case 33:return g===61?(s+=2,"!="):null;case 60:return g===60?y===61?(s+=3,"<<="):(s+=2,"<<"):g===61?(s+=2,"<="):(s++,"<");case 62:return g===62?y===61?(s+=3,">>="):(s+=2,">>"):g===61?(s+=2,">="):(s++,">");case 43:return g===61?(s+=2,"+="):g===43?null:(s++,"+");case 45:return g===61?(s+=2,"-="):g===45?null:(s++,"-");case 42:return g===42?(s+=2,"**"):g===61?(s+=2,"*="):(s++,"*");case 47:return g===61?(s+=2,"/="):(s++,"/");case 37:return g===61?(s+=2,"%="):(s++,"%");case 124:return g===124?(s+=2,"||"):g===61?(s+=2,"|="):(s++,"|");case 38:return g===38?(s+=2,"&&"):g===61?(s+=2,"&="):(s++,"&");case 94:return g===61?(s+=2,"^="):(s++,"^");case 63:return s++,"?";default:return null}}function d(){for(p();s<o;){let g=r.charCodeAt(s);if(g!==33&&g!==126&&g!==43&&g!==45||r.charCodeAt(s+1)===61)break;s++,p()}if(r.charCodeAt(s)===40){let g=1;for(s++;s<o&&g>0;){let y=e?.findArithmeticWordEnd?.(s+t,t+o)??s+t;if(y>s+t){s=y-t;continue}let P=r.charCodeAt(s++);P===40?g++:P===41&&g--}}else s<o&&r.charCodeAt(s)!==41&&r.charCodeAt(s)!==58&&x(0);p();let _=r.charCodeAt(s);(_===43||_===45)&&r.charCodeAt(s+1)===_&&(s+=2)}function l(_){p();let g=s;if(g===o)return h(g,g);let y=e?.embeddedWords.length??0,P=0;for(;;){if(d(),p(),r.charCodeAt(s)===58&&P>0){P--,s++;continue}let w=s,B=c();if(B===null||P===0&&pe(B)<_){s=w;break}B==="?"&&P++}return e&&(e.embeddedWords.length=y),h(g,We(r,g,s))}function m(_,g){if(g>256)return l(_);let y=A(g);for(;p(),!(s>=o);){let P=s,w=c();if(!w)break;if(w==="?"){if(pe(w)<_){s=P;break}let v=m(1,g+1);p(),s<o&&r.charCodeAt(s)===58&&s++;let z=m(3,g+1);y={type:"ArithmeticTernary",pos:y.pos,end:z.end,test:y,consequent:v,alternate:z};continue}let B=pe(w);if(B<_){s=P;break}let L=os(w)?B:B+1,rt=m(L,g+1);y={type:"ArithmeticBinary",pos:y.pos,end:rt.end,operator:w,left:y,right:rt}}return y}function A(_){if(p(),_>256)return l(15);if(s>=o)return h(s,s);let g=s,y=r.charCodeAt(s),P=s+1<o?r.charCodeAt(s+1):0;if(y===43&&P===43){s+=2;let w=A(_+1);return{type:"ArithmeticUnary",pos:g+t,end:w.end,operator:"++",operand:w,prefix:!0}}if(y===45&&P===45){s+=2;let w=A(_+1);return{type:"ArithmeticUnary",pos:g+t,end:w.end,operator:"--",operand:w,prefix:!0}}if(y===33){s++;let w=A(_+1);return{type:"ArithmeticUnary",pos:g+t,end:w.end,operator:"!",operand:w,prefix:!0}}if(y===126){s++;let w=A(_+1);return{type:"ArithmeticUnary",pos:g+t,end:w.end,operator:"~",operand:w,prefix:!0}}if(y===43&&P!==43&&P!==61){s++;let w=A(_+1);return{type:"ArithmeticUnary",pos:g+t,end:w.end,operator:"+",operand:w,prefix:!0}}if(y===45&&P!==45&&P!==61){s++;let w=A(_+1);return{type:"ArithmeticUnary",pos:g+t,end:w.end,operator:"-",operand:w,prefix:!0}}return k(_)}function k(_){let g=x(_);if(p(),s+1<o){let y=r.charCodeAt(s),P=r.charCodeAt(s+1);if(y===43&&P===43)return s+=2,{type:"ArithmeticUnary",pos:g.pos,end:s+t,operator:"++",operand:g,prefix:!1};if(y===45&&P===45)return s+=2,{type:"ArithmeticUnary",pos:g.pos,end:s+t,operator:"--",operand:g,prefix:!1}}return g}function x(_){if(p(),s>=o)return h(s,s);let g=r.charCodeAt(s);if(g===40){let L=s;s++;let rt=m(0,_+1);return p(),s<o&&r.charCodeAt(s)===41&&s++,{type:"ArithmeticGroup",pos:L+t,end:s+t,expression:rt}}if(g===36){let L=s,rt=e?.embeddedWords.length??0,v=f(),z=e?.findArithmeticWordEnd?.(L+t,t+o)??s+t;return z>s+t?(e&&(e.embeddedWords.length=rt),s=z-t,h(L,s,!0)):v}if(g===96||g===34||g===39){let L=s;return s=(e?.findArithmeticWordEnd?.(L+t,t+o)??L+t+1)-t,h(L,s,!0)}let y=s,P=e?.embeddedWords.length??0,w=C(),B=e?.findArithmeticWordEnd?.(y+t,t+o)??s+t;return B>s+t?(e&&(e.embeddedWords.length=P),s=B-t,h(y,s,!0)):w}function f(){let _=s;if(s++,s>=o)return h(_,s);let g=r.charCodeAt(s);if(g===40)if(s+1<o&&r.charCodeAt(s+1)===40){let y=e?.findArithmeticExpansionEnd(_+t,t+o)??-1;if(y!==-1)s=y-t;else{s+=2;let P=1;for(;s<o&&P>0;)r.charCodeAt(s)===40&&r.charCodeAt(s+1)===40?(P++,s+=2):r.charCodeAt(s)===41&&r.charCodeAt(s+1)===41?(P--,s+=2):s++}}else{s++;let y=e?.findClosingParenthesis(s+t,t+o)??-1;if(y!==-1)s=y-t+1;else{let w=1;for(;s<o&&w>0;){let B=r.charCodeAt(s++);B===40?w++:B===41&&w--}}let P=r.slice(_,s);return e?e.commandExpansion(_+t,s+t,P):new pt("ArithmeticCommandExpansion",_+t,s+t,P)}else if(g===123){let y=e?.findClosingBrace(s+t+1,t+o)??-1;if(y!==-1)s=y-t+1;else{s++;let P=1;for(;s<o&&P>0;){let w=r.charCodeAt(s++);w===123?P++:w===125&&P--}}}else for(;s<o;){let y=r.charCodeAt(s);if(y>=97&&y<=122||y>=65&&y<=90||y>=48&&y<=57||y===95)s++;else break}return h(_,s,g===40||g===123)}function C(){let _=s;for(;s<o;){let g=r.charCodeAt(s);if(g>=48&&g<=57||g>=65&&g<=90||g>=97&&g<=122||g===95||g===35)s++;else break}if(s>_&&s<o&&r.charCodeAt(s)===91){let g=e?.findClosingBracket?.(s+t+1,t+o)??-1;if(g!==-1)s=g-t+1;else{s++;let y=1;for(;s<o&&y>0;){let P=r.charCodeAt(s);P===91?y++:P===93&&y--,s++}}return h(_,s,!0)}return s===_&&s++,h(_,s)}if(p(),s>=o)return null;let E=s,b=m(0,i);return p(),s<o?(e&&(e.embeddedWords.length=n),h(E,We(r,E,o),e!==void 0)):b}var te=(r,t)=>St(t.text)?At(t.text,0,t.text.length,void 0,t.pos):void 0;function vt(r){return r.wordParts??(r.wordParts=(t,e,i)=>rs(t,e,i,r))}function ee(r){return r.embeddedParts??(r.embeddedParts=(t,e,i)=>ue(t,e,i,r))}function Re(r){return r.arrayParts??(r.arrayParts=(t,e,i)=>hs(t,e,i,r))}function rs(r,t,e=0,i=Wt()){let s=new ct(r,t.pos,t.end,!1,i);return s._nestingDepth=e,s.buildWordParts(t.pos)??void 0}function ue(r,t,e=0,i,s=!1){if(!It(r,t.pos,t.end))return;let o=new ct(r,t.pos,t.end,!1,i);return o._nestingDepth=e,o.buildEmbeddedWordParts(t.pos,s)??void 0}function hs(r,t,e=0,i){let s=new ct(r,t.pos,t.end,!1,i);return s._nestingDepth=e,s.buildArrayElementParts(t.pos)??void 0}function Le(r,t,e=0,i){let s=new ct(r,t.pos,t.end,!1,i);return s._nestingDepth=e,s.buildHereDocParts(t.pos,t.end)??void 0}function Ft(r,t,e,i,s,o=i){if(!It(e,t,t+r.length))return le(r,t,void 0,i)??void 0;let n=[],h=new ct(e,0,e.length,!1,s);h._nestingDepth=i;let p=le(r,t,{embeddedWords:n,commandExpansion:(c,d,l)=>new pt("ArithmeticCommandExpansion",c,d,l,{source:e,start:c+2,end:d-1,depth:i+1,parenBoundary:!0,context:s}),findClosingBracket:(c,d)=>h.findClosingBracket(c,d),findClosingBrace:(c,d)=>h.findClosingBrace(c,d),findClosingParenthesis:(c,d)=>h.findClosingParenthesis(c,d),findArithmeticExpansionEnd:(c,d)=>h.findArithmeticExpansionEnd(c,d),findArithmeticWordEnd:(c,d)=>h.findArithmeticWordEnd(c,d)},i)??void 0;if(o<=256)for(let c of n)c.parts=ue(e,c,o,s,!0);return p}var fe=class{type="ArrayValue";pos;end;#t;#e;#i;#s;constructor(t,e,i,s,o){this.pos=e,this.end=i,this.#t=t,this.#e=s,this.#i=o}get elements(){if(this.#s)return this.#s;let t=new ct(this.#t,this.pos+1,this.end-1,!1,this.#i);t._nestingDepth=this.#e;let e=[];for(;t.peek(u.ArrayElement).token!==a.EOF;){let i=t.next(u.ArrayElement);if(i.token!==a.Word&&i.token!==a.Assignment)continue;let s=i.raw?i.value:this.#t.slice(i.pos,i.end),o=s.charCodeAt(0)===91,n=t.getSubstitutionContext(),h=!o&&i.raw&&i.keywordEligible?void 0:this.#t,p=h===void 0?te:o?Re(n):vt(n);e.push(new Z(s,i.pos,i.end,h,p,this.#e))}return this.#s=e,e}toJSON(){return{type:this.type,pos:this.pos,end:this.end,elements:this.elements}}},se=class{type="Assignment";pos;end;text;#t;#e;#i;#s;#n=null;#h;#r;#o=null;constructor(t,e,i,s,o,n,h){this.pos=e,this.end=i,this.text=t,this.#t=s,this.#s=o,this.#e=n,this.#i=h}get name(){return this.#n??this.resolveTarget()}get append(){return this.#n===null&&this.resolveTarget(),this.#h}get index(){return this.#n===null&&this.resolveTarget(),this.#r}get value(){if(this.#o!==null)return this.#o;let t=this.#s+1,e=ht(this.#t,t,this.end);return this.#o=this.#t.charCodeAt(e)===40&&this.#t.charCodeAt(this.end-1)===41?new fe(this.#t,e,this.end,this.#e,this.#i):new Z(this.#t.slice(t,this.end),t,this.end,this.#t,vt(this.#i),this.#e),this.#o}resolveTarget(){let t=this.#s-this.pos,e=t,i=t;for(;i>=2&&this.text.charCodeAt(i-2)===92&&this.text.charCodeAt(i-1)===10;)i-=2;this.text.charCodeAt(i-1)===43&&(this.#h=!0,e=i-1);let s=this.text.indexOf("[");if(s>0&&s<e){let n=this.text.lastIndexOf("]",t);if(n>s){let h=this.pos+s+1,p=this.pos+n;this.#r=new Z(this.#t.slice(h,p),h,p,this.#t,ee(this.#i),this.#e),e=s}}let o=this.text.slice(0,e);return this.#n=o.includes(`\\
`)?o.split(`\\
`).join(""):o}toJSON(){return{type:this.type,pos:this.pos,end:this.end,text:this.text,name:this.name,append:this.append,index:this.index,value:this.value}}};var me=r=>r.type==="Word"||r.type==="Assignment",Gt=class{type="Command";pos;end;name;prefix;suffix;#t;#e;constructor(t,e,i,s,o){this.pos=t,this.end=e,this.name=i,this.prefix=s,this.suffix=o}get args(){if(this.#t===void 0){let t=this.suffix;this.#t=t.every(me)?t:t.filter(me)}return this.#t}get redirects(){if(this.#e===void 0){let t=[];for(let e of this.prefix)e.type!=="Assignment"&&t.push(e);for(let e of this.suffix)me(e)||t.push(e);this.#e=t}return this.#e}toJSON(){return{type:this.type,pos:this.pos,end:this.end,name:this.name,prefix:this.prefix,suffix:this.suffix}}};var ie=class{type="HereDocBody";pos;end;text="";#t;#e;#i;#s;constructor(t,e,i,s,o){this.pos=e,this.end=e,this.#t=t,this.#e=s,this.#i=o,this.#s=i?void 0:null}get parts(){return this.#s===null&&(this.#s=Le(this.#t,this,this.#e,this.#i)),this.#s}toJSON(){return{type:this.type,pos:this.pos,end:this.end,text:this.text,parts:this.parts}}};ct._findSubstitutionEnd=ms;pt._resolveScript=fs;Nt._resolveExpression=Ft;function as(r){switch(r.length){case 5:return r==="local"||r==="alias";case 6:return r==="export";case 7:return r==="declare"||r==="typeset";case 8:return r==="readonly";default:return!1}}var xe=class{type="ArithmeticCommand";pos;end;body;#t;#e;#i;#s=null;constructor(t,e,i,s,o,n){this.pos=t,this.end=e,this.body=i,this.#t=s,this.#e=o,this.#i=n}get expression(){return this.#s===null&&(this.#s=Ft(this.body,this.pos+2,this.#t,this.#e,this.#i)),this.#s}set expression(t){this.#s=t??void 0}toJSON(){return{type:this.type,pos:this.pos,end:this.end,expression:this.expression,body:this.body}}},$t=class{type="ArithmeticFor";pos;end;body;#t;#e;#i;#s;#n;#h;#r;#o;#p;#a=null;#c=null;#d=null;constructor(t,e,i,s,o,n,h,p,c,d,l,m){this.pos=t,this.end=e,this.body=i,this.#t=s,this.#e=o,this.#i=n,this.#s=h,this.#n=p,this.#h=c,this.#r=d,this.#o=l,this.#p=m}get initialize(){return this.#a===null&&(this.#t?this.#a=Ft(this.#t,this.#s,this.#r,this.#o,this.#p):this.#a=void 0),this.#a}set initialize(t){this.#a=t??void 0}get test(){return this.#c===null&&(this.#e?this.#c=Ft(this.#e,this.#n,this.#r,this.#o,this.#p):this.#c=void 0),this.#c}set test(t){this.#c=t??void 0}get update(){return this.#d===null&&(this.#i?this.#d=Ft(this.#i,this.#h,this.#r,this.#o,this.#p):this.#d=void 0),this.#d}set update(t){this.#d=t??void 0}toJSON(){return{type:this.type,pos:this.pos,end:this.end,initialize:this.initialize,test:this.test,update:this.update,body:this.body}}},cs={[a.DoubleSemi]:";;",[a.SemiAmp]:";&",[a.DoubleSemiAmp]:";;&"},ds={">":">",">>":">>","<":"<","<>":"<>","<&":"<&",">&":">&",">|":">|","&>":"&>","&>>":"&>>"},ot=new Uint8Array(37);ot[a.EOF]=1;ot[a.RParen]=1;ot[a.RBrace]=1;ot[a.Then]=1;ot[a.Else]=1;ot[a.Elif]=1;ot[a.Fi]=1;ot[a.Do]=1;ot[a.Done]=1;ot[a.Esac]=1;ot[a.DoubleSemi]=1;ot[a.SemiAmp]=1;ot[a.DoubleSemiAmp]=1;var wt=new Uint8Array(37);wt[a.RParen]=1;wt[a.RBrace]=1;wt[a.DblRBracket]=1;wt[a.Fi]=1;wt[a.Done]=1;wt[a.Esac]=1;wt[a.ArithCmd]=1;function Oe(r){return r.token===a.Word&&r.keywordEligible&&r.value==="!"}var et=new Uint8Array(37);et[a.Word]=1;et[a.Assignment]=1;et[a.Bang]=1;et[a.LParen]=1;et[a.LBrace]=1;et[a.DblLBracket]=1;et[a.If]=1;et[a.For]=1;et[a.While]=1;et[a.Until]=1;et[a.Case]=1;et[a.Function]=1;et[a.Select]=1;et[a.ArithCmd]=1;et[a.Coproc]=1;et[a.Redirect]=1;var ps={"-a":1,"-b":1,"-c":1,"-d":1,"-e":1,"-f":1,"-g":1,"-h":1,"-k":1,"-p":1,"-r":1,"-s":1,"-t":1,"-u":1,"-v":1,"-w":1,"-x":1,"-z":1,"-n":1,"-o":1,"-N":1,"-S":1,"-L":1,"-G":1,"-O":1,"-R":1},ls={"==":1,"!=":1,"=~":1,"=":1,"-eq":1,"-ne":1,"-lt":1,"-le":1,"-gt":1,"-ge":1,"-nt":1,"-ot":1,"-ef":1,"<":1,">":1},Ie=[],us=[],Ce=[];function Fe(r){return new Kt(r,0,r.length).run()}function fs(r,t,e,i=0,s=!1,o){return new Kt(r,t,e,i,s,void 0,o).run()}var Ae=class r extends ct{boundaries;pending;context;constructor(t,e,i,s,o,n=!0){super(t,e.start,e.end,n,o),this._nestingDepth=e.depth,this.boundaries=i,this.pending=s,this.context=o}fork(t,e,i=!1){return new r(this.getSource(),{start:t,end:e,depth:this._nestingDepth},this.boundaries,this.pending,this.context,i)}findSubstitutionEnd(t,e,i=this._nestingDepth+1){let s=this.context.boundaries?.get(t);if(s!==void 0)return s<=e?s:-1;let o=this.boundaries.get(t);return o!==void 0?o<=e?o:-1:(this.boundaries.has(t)||(this.boundaries.set(t,void 0),this.pending.push({start:t,end:e,depth:i})),-1)}};function ms(r,t,e,i,s){let o=new Map([[t,void 0]]),n=[{start:t,end:e,depth:i}];for(;n.length>0;){let h=n.length,p=n[h-1],c=new Ae(r,p,o,n,s),d=new Kt(r,p.start,p.end,p.depth,!0,c).findSubstitutionEnd();n.length===h&&(o.set(p.start,d),d!==-1&&re(s).set(p.start,d),n.pop())}return o.get(t)??-1}var Kt=class{tok;source;start;end;depth;errors=null;syntaxDepth=0;constructor(t,e,i,s=0,o=!1,n,h){this.tok=n??new ct(t,e,i,o,Wt(h)),this.tok._nestingDepth=s,this.source=t,this.start=e,this.end=i,this.depth=s}run(){let t=this.start;this.depth>256&&this.error("maximum substitution nesting depth exceeded",t);let e;if(t===0&&this.source.charCodeAt(0)===35&&this.source.charCodeAt(1)===33){let o=this.source.indexOf(`
`);e=o===-1?this.source:this.source.slice(0,o)}let i=this.list();for(;;){let o=this.tok.peek(u.CommandStart);if(o.token===a.EOF||(this.unexpectedToken(o),!ot[o.token]&&o.token!==a.In))break;this.tok.next(u.CommandStart);let n=this.tok.peek(u.CommandStart).token;if(n!==a.Semi&&n!==a.Newline&&n!==a.Amp)break;for(;n===a.Semi||n===a.Newline||n===a.Amp;)this.tok.next(u.CommandStart),n=this.tok.peek(u.CommandStart).token;let h=this.list();for(let p=0;p<h.length;p++)i.push(h[p])}let s=this.tok._errors;if(s!==null&&s.length>0){let o=this.errors??(this.errors=[]);for(let n=0;n<s.length;n++)o.push(s[n])}return this.errors!==null&&this.errors.length>1&&this.errors.sort((o,n)=>o.pos-n.pos),{type:"Script",pos:t,end:this.end,shebang:e,commands:i,errors:this.errors??void 0}}error(t,e){(this.errors??(this.errors=[])).push({message:t,pos:e})}unexpectedToken(t){this.error(`unexpected token '${t.token===a.Newline?"newline":t.value}'`,t.pos)}findSubstitutionEnd(){return this.list(),this.acceptEnd(a.RParen)}skipSemi(){this.tok.peek(u.Normal).token===a.Semi&&this.tok.next(u.Normal)}accept(t,e=u.Normal){return this.tok.peek(e).token===t?this.tok.next(e):null}acceptEnd(t,e=u.Normal){return this.tok.peek(e).token===t?this.tok.next(e).end:-1}skipNewlines(t=u.Normal){for(;this.tok.peek(t).token===a.Newline;)this.tok.next(t)}doBody(t){let e=this.list(),i=t?this.emptyListPos(e):-1;return i>=0&&this.error("expected command after 'do'",i),e}emptyListPos(t){return t.length===0?this.tok.peek(u.CommandStart).pos:-1}makeStatement(t){return{type:"Statement",pos:t.pos,end:t.end,command:t,background:void 0}}list(){let t=[];this.skipNewlines(u.CommandStart);let e=this.tok.peek(u.CommandStart).token;if(ot[e]||!et[e])return t;let i=this.andOr();for(i&&t.push(this.makeStatement(i));e=this.tok.peekFollow(wt).token,!(e!==a.Semi&&e!==a.Newline&&e!==a.Amp);){let s=e===a.Amp,o=this.tok.next(u.Normal).end;if(s){let h=t[t.length-1];h.background=!0,h.end=o}if(this.skipNewlines(u.CommandStart),e=this.tok.peek(u.CommandStart).token,ot[e]||!et[e])break;let n=this.andOr();n&&t.push(this.makeStatement(n))}return t}andOr(){let t=this.pipeline();if(!t)return null;let e=this.tok.peek(u.Normal).token;if(e!==a.And&&e!==a.Or)return t;let i=[t],s=[];do{let o=this.tok.next(u.Normal),n=o.token===a.And?"&&":"||";this.skipNewlines(u.CommandStart);let h=this.pipeline();if(!h){this.error(`expected command after '${n}'`,o.end);break}s.push(n),i.push(h),e=this.tok.peek(u.Normal).token}while(e===a.And||e===a.Or);return{type:"AndOr",pos:t.pos,end:i[i.length-1].end,commands:i,operators:s}}withRedirects(t){let e=this.collectTrailingRedirects();return e.length===0?t:{type:"Redirected",pos:t.pos,end:e[e.length-1].end,command:t,redirects:e}}pipeline(){let t;for(;;){let s=this.tok.peek(u.CommandStart),o=s.token===a.Word&&s.keywordEligible&&s.value==="time";if(s.token!==a.Bang&&!o)break;let n=this.tok.next(u.CommandStart),h=n.pos,p=n.end,c,d;if(o){let l=this.tok.peek(u.CommandStart);l.token===a.Word&&l.keywordEligible&&l.value==="-p"&&(this.tok.next(u.CommandStart),c={pos:l.pos,end:l.end},l=this.tok.peek(u.CommandStart)),l.token===a.Word&&l.keywordEligible&&l.value==="--"&&(this.tok.next(u.CommandStart),d={pos:l.pos,end:l.end})}t===void 0&&(t=[]),t.push(o?{type:"Time",pos:h,end:d?.end??c?.end??p,keywordEnd:p,posix:c,endOfOptions:d}:{type:"Negation",pos:h,end:p})}let e=this.pipelineCommands();if(!t)return e;let i=t[0].pos;return e?.type==="Pipeline"?(e.prefixes=t,e.pos=i,e):{type:"Pipeline",pos:i,end:e?e.end:t[t.length-1].end,prefixes:t,commands:e?[e]:[],operators:[]}}pipelineCommands(){let t=this.command();if(!t)return null;if(this.tok.peek(u.Normal).token!==a.Pipe)return t;let e=[t],i=[];for(;this.tok.peek(u.Normal).token===a.Pipe;){let o=this.tok.next(u.Normal),n=o.value==="|&"?"|&":"|";this.skipNewlines(u.CommandStart);let h=this.command();if(!h){this.error(`expected command after '${n}'`,o.end);break}i.push(n),e.push(h)}return e.length===1?e[0]:{type:"Pipeline",pos:t.pos,end:e[e.length-1].end,prefixes:us,commands:e,operators:i}}command(){let t;switch(this.tok.peek(u.CommandStart).token){case a.LParen:t=this.subshell();break;case a.LBrace:t=this.braceGroup();break;case a.If:t=this.ifClause();break;case a.For:t=this.forClause();break;case a.While:t=this.whileClause();break;case a.Until:t=this.untilClause();break;case a.Case:t=this.caseClause();break;case a.Function:return this.functionDef();case a.Select:t=this.selectClause();break;case a.DblLBracket:t=this.testCommand();break;case a.ArithCmd:t=this.arithCommand();break;case a.Coproc:return this.coprocCommand();case a.Word:case a.Assignment:case a.Redirect:return this.simpleCommandOrFunction(!1);default:return null}return this.withRedirects(t)}collectTrailingRedirects(){let t=Ie;for(;this.tok.peekFollow(wt).token===a.Redirect;)t===Ie&&(t=[]),t.push(this.readRedirect(u.Normal));return t}arithCommand(){let t=this.tok.next(u.CommandStart);return new xe(t.pos,t.end,t.value,this.source,this.depth,this.tok.getSubstitutionContext())}coprocCommand(){let t=this.tok.next(u.CommandStart),e=t.pos,i=this.tok.peek(u.CommandStart),s;if(i.token===a.Word){let h=this.tok.fork(i.end,this.end);switch(h.peek(u.CommandStart).token){case a.LParen:h.next(u.CommandStart),h.peek(u.Normal).token!==a.RParen&&(s=this.readWord(u.CommandStart));break;case a.LBrace:case a.If:case a.For:case a.While:case a.Until:case a.Case:case a.Select:case a.DblLBracket:case a.ArithCmd:s=this.readWord(u.CommandStart)}}let o=i.token===a.Word&&s===void 0?this.simpleCommandOrFunction(!0):this.command();if(o&&o.type!=="Function"&&o.type!=="Coproc")return{type:"Coproc",pos:e,end:o.end,name:s,body:o};this.error("expected command after 'coproc'",o?.pos??t.end);let n=o?this.makeCompoundList([this.makeStatement(o)]):{type:"CompoundList",pos:t.end,end:t.end,commands:[]};return{type:"Coproc",pos:e,end:n.end,name:s,body:n}}subshell(){return this.subshellBody(this.tok.next(u.CommandStart).pos)}subshellBody(t){if(this.syntaxDepth>=256){this.error("maximum subshell nesting depth exceeded",t);let o=this.tok.skipSubshellBody();o<0&&this.error("expected ')' to close subshell",this.tok.getPos());let n=o>=0?o:t;return{type:"Subshell",pos:t,end:n,body:this.makeCompoundList([])}}this.syntaxDepth++;let e=this.list();this.syntaxDepth--,e.length===0&&this.error("expected command in subshell",t);let i=this.acceptEnd(a.RParen,u.Normal);i<0&&this.error("expected ')' to close subshell",this.tok.getPos());let s=i>=0?i:t;return{type:"Subshell",pos:t,end:s,body:this.makeCompoundList(e)}}braceGroup(){let t=this.tok.next(u.CommandStart).pos;if(this.syntaxDepth>=256){this.error("maximum brace group nesting depth exceeded",t);let o=this.tok.skipCompoundBody(a.RBrace);o<0&&this.error("expected '}' to close brace group",this.tok.getPos());let n=o>=0?o:t;return{type:"BraceGroup",pos:t,end:n,body:this.makeCompoundList([])}}this.syntaxDepth++;let e=this.list();this.syntaxDepth--,e.length===0&&this.error("expected command in brace group",t);let i=this.acceptEnd(a.RBrace,u.Normal);i<0&&this.error("expected '}' to close brace group",this.tok.getPos());let s=i>=0?i:t;return{type:"BraceGroup",pos:t,end:s,body:this.makeCompoundList(e)}}ifClause(){let t=this.tok.next(u.CommandStart).pos;if(this.syntaxDepth>=256){this.error("maximum if nesting depth exceeded",t);let o=this.tok.skipCompoundBody(a.Fi);o<0&&this.error("expected 'fi' to close 'if'",this.tok.getPos());let n=o>=0?o:t;return{type:"If",pos:t,end:n,clause:this.makeCompoundList([]),then:this.makeCompoundList([]),elif:Ce,else:void 0}}this.syntaxDepth++;let e,i=t;for(;;){let o=this.list(),n=this.emptyListPos(o),h=this.makeCompoundList(o);this.skipSemi();let p=this.accept(a.Then,u.CommandStart);p?n>=0&&this.error(`expected command after '${i===t?"if":"elif"}'`,n):this.error("expected 'then'",this.tok.getPos());let c=this.list(),d=this.makeCompoundList(c);p&&c.length===0&&(this.error("expected command after 'then'",this.tok.peek(u.CommandStart).pos),d.pos=p.end,d.end=p.end),e?(e.elif===Ce&&(e.elif=[]),e.elif.push({type:"Elif",pos:i,end:d.end,clause:h,then:d})):e={type:"If",pos:t,end:t,clause:h,then:d,elif:Ce,else:void 0},this.skipSemi();let l=this.accept(a.Elif,u.CommandStart);if(!l)break;i=l.pos}if(this.accept(a.Else,u.CommandStart)){let o=this.list(),n=this.emptyListPos(o);n>=0&&this.error("expected command after 'else'",n),e.else=this.makeCompoundList(o),this.skipSemi()}let s=this.acceptEnd(a.Fi,u.CommandStart);return s<0&&this.error("expected 'fi' to close 'if'",this.tok.getPos()),e.end=s>=0?s:i,this.syntaxDepth--,e}forClause(){let t=this.tok.next(u.CommandStart).pos;if(this.tok.peek(u.Normal).token===a.LParen)return this.cStyleFor(t);let e=this.readRequiredWord("for"),i;if(this.skipNewlines(u.CommandStart),this.tok.peek(u.CommandStart).token===a.In)for(this.tok.next(u.CommandStart),i=[];this.tok.peek(u.Normal).token===a.Word;)i.push(this.readWord(u.Normal));if(this.skipSemi(),this.skipNewlines(u.CommandStart),this.tok.peek(u.CommandStart).token===a.LBrace){let p=this.braceGroup();return{type:"For",pos:t,end:p.end,name:e,wordlist:i,body:p.body}}let s=this.accept(a.Do,u.CommandStart)!==null;if(s||this.error("expected 'do'",this.tok.getPos()),this.syntaxDepth>=256){this.error("maximum for nesting depth exceeded",t);let p=this.tok.skipCompoundBody(a.Done);p<0&&this.error("expected 'done' to close 'for'",this.tok.getPos());let c=p>=0?p:t;return{type:"For",pos:t,end:c,name:e,wordlist:i,body:this.makeCompoundList([])}}this.syntaxDepth++;let o=this.doBody(s);this.syntaxDepth--,this.skipSemi();let n=this.acceptEnd(a.Done,u.CommandStart);n<0&&this.error("expected 'done' to close 'for'",this.tok.getPos());let h=n>=0?n:t;return{type:"For",pos:t,end:h,name:e,wordlist:i,body:this.makeCompoundList(o)}}cStyleFor(t){let[e,i,s,o,n,h]=this.tok.readCStyleForExprs();if(this.tok.peek(u.CommandStart).token===a.Semi&&this.tok.next(u.CommandStart),this.skipNewlines(u.CommandStart),this.tok.peek(u.CommandStart).token===a.LBrace){let m=this.braceGroup();return new $t(t,m.end,m.body,e,i,s,o,n,h,this.source,this.depth,this.tok.getSubstitutionContext())}let p=this.accept(a.Do,u.CommandStart)!==null;if(p||this.error("expected 'do'",this.tok.getPos()),this.syntaxDepth>=256){this.error("maximum for nesting depth exceeded",t);let m=this.tok.skipCompoundBody(a.Done);m<0&&this.error("expected 'done' to close 'for'",this.tok.getPos());let A=m>=0?m:t;return new $t(t,A,this.makeCompoundList([]),e,i,s,o,n,h,this.source,this.depth,this.tok.getSubstitutionContext())}this.syntaxDepth++;let c=this.doBody(p);this.syntaxDepth--;let d=this.acceptEnd(a.Done,u.CommandStart);d<0&&this.error("expected 'done' to close 'for'",this.tok.getPos());let l=d>=0?d:t;return new $t(t,l,this.makeCompoundList(c),e,i,s,o,n,h,this.source,this.depth,this.tok.getSubstitutionContext())}whileClause(){return this.whileOrUntil("while")}untilClause(){return this.whileOrUntil("until")}whileOrUntil(t){let e=this.tok.next(u.CommandStart).pos;if(this.syntaxDepth>=256){this.error(`maximum ${t} nesting depth exceeded`,e);let d=this.tok.skipCompoundBody(a.Done);d<0&&this.error(`expected 'done' to close '${t}'`,this.tok.getPos());let l=d>=0?d:e;return{type:"While",pos:e,end:l,kind:t,clause:this.makeCompoundList([]),body:this.makeCompoundList([])}}this.syntaxDepth++;let i=this.list(),s=this.emptyListPos(i),o=this.makeCompoundList(i);this.skipSemi();let n=this.accept(a.Do,u.CommandStart)!==null;n?s>=0&&this.error(`expected command after '${t}'`,s):this.error("expected 'do'",this.tok.getPos());let h=this.doBody(n);this.skipSemi();let p=this.acceptEnd(a.Done,u.CommandStart);p<0&&this.error(`expected 'done' to close '${t}'`,this.tok.getPos());let c=p>=0?p:e;return this.syntaxDepth--,{type:"While",pos:e,end:c,kind:t,clause:o,body:this.makeCompoundList(h)}}caseClause(){let t=this.tok.next(u.CommandStart).pos,e=this.readRequiredWord("case");if(this.skipNewlines(u.CommandStart),this.accept(a.In,u.CommandStart)||this.error("expected 'in' after 'case' word",this.tok.getPos()),this.skipNewlines(u.CasePattern),this.syntaxDepth>=256){this.error("maximum case nesting depth exceeded",t);let p=this.tok.skipCompoundBody(a.Esac);p<0&&this.error("expected 'esac' to close 'case'",this.tok.getPos());let c=p>=0?p:t;return{type:"Case",pos:t,end:c,word:e,items:[]}}this.syntaxDepth++;let i=[],s=!1,o=this.tok.peek(u.CasePattern).token;for(;o!==a.Esac&&o!==a.EOF;){let p=this.tok.peek(u.Normal).pos;this.accept(a.LParen,u.Normal);let c=[],d=!0,l=this.tok.peek(u.Normal);for(;l.token!==a.RParen&&l.token!==a.EOF;){l.token===(d?a.Word:a.Pipe)?d=!d:s||(this.unexpectedToken(l),s=!0);let b=this.tok.next(u.Normal);b.token===a.Word&&c.push(this.toWord(b)),l=this.tok.peek(u.Normal)}d&&l.token===a.RParen&&!s&&(this.unexpectedToken(l),s=!0);let m=this.acceptEnd(a.RParen,u.Normal),A=this.list(),k=m>=0?m:c.length>0?c[c.length-1].end:p,x=A.length>0?A[A.length-1].end:k,f={type:"CaseItem",pos:p,end:x,pattern:c,body:A.length>0?this.makeCompoundList(A):{type:"CompoundList",pos:k,end:k,commands:[]},terminator:void 0},C=this.tok.peek(u.CommandStart);if(o=C.token,o===a.DoubleSemi||o===a.SemiAmp||o===a.DoubleSemiAmp){let E=this.tok.next(u.CommandStart);f.terminator=cs[E.token],f.end=E.end}else o!==a.Esac&&o!==a.EOF&&!s&&(this.unexpectedToken(C),s=!0);i.push(f),this.skipNewlines(u.CasePattern),o=this.tok.peek(u.CasePattern).token}let n=this.acceptEnd(a.Esac,u.CommandStart);n<0&&this.error("expected 'esac' to close 'case'",this.tok.getPos());let h=n>=0?n:t;return this.syntaxDepth--,{type:"Case",pos:t,end:h,word:e,items:i}}selectClause(){let t=this.tok.next(u.CommandStart).pos,e=this.readRequiredWord("select"),i;if(this.skipNewlines(u.CommandStart),this.tok.peek(u.CommandStart).token===a.In)for(this.tok.next(u.CommandStart),i=[];this.tok.peek(u.Normal).token===a.Word;)i.push(this.readWord(u.Normal));if(this.skipSemi(),this.skipNewlines(u.CommandStart),this.tok.peek(u.CommandStart).token===a.LBrace){let p=this.braceGroup();return{type:"Select",pos:t,end:p.end,name:e,wordlist:i,body:p.body}}let s=this.accept(a.Do,u.CommandStart)!==null;if(s||this.error("expected 'do'",this.tok.getPos()),this.syntaxDepth>=256){this.error("maximum select nesting depth exceeded",t);let p=this.tok.skipCompoundBody(a.Done);p<0&&this.error("expected 'done' to close 'select'",this.tok.getPos());let c=p>=0?p:t;return{type:"Select",pos:t,end:c,name:e,wordlist:i,body:this.makeCompoundList([])}}this.syntaxDepth++;let o=this.doBody(s);this.syntaxDepth--,this.skipSemi();let n=this.acceptEnd(a.Done,u.CommandStart);n<0&&this.error("expected 'done' to close 'select'",this.tok.getPos());let h=n>=0?n:t;return{type:"Select",pos:t,end:h,name:e,wordlist:i,body:this.makeCompoundList(o)}}testCommand(){let t=this.tok.next(u.CommandStart).pos,e=this.parseTestOr(),i=this.acceptEnd(a.DblRBracket,u.TestMode);i<0&&this.error("expected ']]' to close '[['",this.tok.getPos());let s=i>=0?i:t;return{type:"TestCommand",pos:t,end:s,expression:e}}parseTestOr(){let t=this.parseTestAnd();if(this.tok.peek(u.TestMode).token!==a.Or)return t;let e=[t];do this.tok.next(u.TestMode),e.push(this.parseTestAnd());while(this.tok.peek(u.TestMode).token===a.Or);return{type:"TestLogical",pos:t.pos,end:e[e.length-1].end,operator:"||",operands:e}}parseTestAnd(){let t=this.parseTestNot();if(this.tok.peek(u.TestMode).token!==a.And)return t;let e=[t];do this.tok.next(u.TestMode),e.push(this.parseTestNot());while(this.tok.peek(u.TestMode).token===a.And);return{type:"TestLogical",pos:t.pos,end:e[e.length-1].end,operator:"&&",operands:e}}parseTestNot(){let t=this.tok.peek(u.TestMode);if(!Oe(t))return this.parseTestPrimary();let e=[];for(;Oe(t);){let s=this.tok.next(u.TestMode);e.push({type:"Negation",pos:s.pos,end:s.end}),t=this.tok.peek(u.TestMode)}let i=this.parseTestPrimary();return{type:"TestNot",pos:e[0].pos,end:i.end,negations:e,operand:i}}parseTestPrimary(){if(this.tok.peek(u.TestMode).token===a.LParen){let h=this.tok.next(u.TestMode).pos;if(this.syntaxDepth>=256){this.error("maximum test group nesting depth exceeded",h);let l=this.tok.skipTestGroup();l<0&&this.error("expected ')' to close test group",this.tok.getPos());let m=l>=0?l:h,A=new Z("",h,h);return{type:"TestGroup",pos:h,end:m,expression:{type:"TestUnary",pos:h,end:h,operator:"-n",operand:A}}}this.syntaxDepth++;let p=this.parseTestOr();this.syntaxDepth--;let c=this.acceptEnd(a.RParen,u.TestMode);c<0&&this.error("expected ')' to close test group",this.tok.getPos());let d=c>=0?c:h;return{type:"TestGroup",pos:h,end:d,expression:p}}let t=this.tok.next(u.TestMode);(t.token===a.And||t.token===a.Or)&&this.unexpectedToken(t);let e=t.value,i=t.pos,s=t.end;if(t.keywordEligible&&ps[e]===1){if(this.tok.peek(u.TestMode).token===a.Word){let p=this.readTestOperand();return{type:"TestUnary",pos:i,end:p.end,operator:e,operand:p}}this.error("expected operand after unary test operator",s);let h=new Z("",s,s);return{type:"TestUnary",pos:i,end:s,operator:e,operand:h}}let o=this.tok.peek(u.TestMode);if(o.token===a.Word&&o.keywordEligible&&ls[o.value]===1){let h=this.tok.next(u.TestMode).value,p;if(h==="=~"){let d=this.tok.readTestRegexWord();p=new Z(this.source.slice(d.pos,d.end),d.pos,d.end,this.source,ee(this.tok.getSubstitutionContext()),this.depth)}else p=this.readTestOperand();let c=this.toWordFromPosEnd(t,i,s);return{type:"TestBinary",pos:i,end:p.end,operator:h,left:c,right:p}}let n=this.toWordFromPosEnd(t,i,s);return{type:"TestUnary",pos:i,end:n.end,operator:"-n",operand:n}}readTestOperand(){let t=this.tok.next(u.TestMode);return t.keywordEligible&&(t.value==="<"||t.value===">")&&this.error(`unexpected argument '${t.value}' to conditional operator`,t.pos),this.toWord(t)}functionDef(){let t=this.tok.next(u.CommandStart).pos,e=this.readWord(u.FunctionName),i;if(this.tok.peek(u.CommandStart).token===a.LParen){let s=this.tok.next(u.CommandStart).pos;this.tok.peek(u.CommandStart).token===a.RParen?(this.tok.next(u.CommandStart),this.skipNewlines(u.CommandStart),i=this.commandAsBody()):i=this.withRedirects(this.subshellBody(s))}else this.skipNewlines(u.CommandStart),i=this.commandAsBody();return{type:"Function",pos:t,end:i.end,name:e,body:i}}simpleCommandOrFunction(t){let e=[],i=this.tok.peek(u.CommandStart).pos,s=i,o=u.CommandStart;for(;;){let d=this.tok.peek(o).token;if(d===a.Assignment){let l=this.tok.next(o);s=l.end,e.push(this.parseAssignment(l))}else if(d===a.Redirect){let l=this.readRedirect(o);e.push(l),s=l.end}else break;o=u.CommandPrefix}if(this.tok.peek(u.Normal).token!==a.Word)return new Gt(i,s,void 0,e,[]);let n=this.tok.next(u.Normal),h=n.keywordEligible?n.value:"",p=this.toWord(n);o=as(h)?u.Declaration:h==="eval"||h==="let"?u.EvalArgument:u.Normal,s=p.end;let c=[];if(t)for(;this.tok.peek(u.CommandPrefix).token===a.Assignment;){let d=this.tok.next(u.CommandPrefix),l=o===u.Declaration?this.parseAssignment(d):this.toWord(d);c.push(l),s=l.end}if(this.tok.peek(o).token===a.LParen&&c.length===0&&(this.tok.next(u.Normal),this.tok.peek(u.Normal).token===a.RParen)){this.tok.next(u.Normal),this.skipNewlines(u.CommandStart);let d=this.commandAsBody();return{type:"Function",pos:p.pos,end:d.end,name:p,body:d}}for(;;){let d=this.tok.peek(o).token;if(d===a.Word||d===a.Assignment){let l=this.tok.next(o),m=d===a.Assignment?this.parseAssignment(l):this.toWord(l);c.push(m),s=m.end}else if(d===a.Redirect){let l=this.readRedirect(o);c.push(l),s=l.end}else break}return new Gt(i,s,p,e,c)}readRedirect(t){let e=this.tok.next(t),i=e.pos,s=e.end,o=e.fileDescriptor!==void 0?{type:"FileDescriptor",pos:i,end:e.descriptorEnd,value:e.fileDescriptor}:e.variableName!==void 0?{type:"FileDescriptorVariable",pos:i,end:e.descriptorEnd,name:e.variableName}:void 0,n=e.targetEnd>e.targetPos;if(n||this.error("expected redirect target",e.targetPos),e.value==="<<"||e.value==="<<-"){let p={type:"HereDoc",pos:i,end:s,operator:e.value,descriptor:o,delimiter:n?{type:"HereDocDelimiter",pos:e.targetPos,end:e.targetEnd,text:this.source.slice(e.targetPos,e.targetEnd),value:e.content??"",quoted:e.delimiterQuoted}:void 0,body:new ie(this.source,s,e.delimiterQuoted,this.depth,this.tok.getSubstitutionContext()),closing:void 0};return n&&this.tok.registerHereDocTarget(p),p}let h=n?new Z(this.source.slice(e.targetPos,e.targetEnd),e.targetPos,e.targetEnd,this.source,vt(this.tok.getSubstitutionContext()),this.depth):void 0;return e.value==="<<<"?{type:"HereString",pos:i,end:s,operator:"<<<",descriptor:o,target:h}:{type:"Redirect",pos:i,end:s,operator:ds[e.value]??">",descriptor:o,target:h}}commandAsBody(){let t=this.command();return t&&t.type!=="Command"&&t.type!=="Function"&&t.type!=="Coproc"?t:(this.error("expected compound command as function body",t?.pos??this.tok.getPos()),this.makeCompoundList(t?[this.makeStatement(t)]:[]))}readWord(t){return this.toWord(this.tok.next(t))}readRequiredWord(t){let e=this.tok.peek(u.Normal);return e.token===a.Word?this.toWord(this.tok.next(u.Normal)):(e.token!==a.EOF&&this.error(`expected word after '${t}'`,e.pos),new Z("",e.pos,e.pos))}toWord(t){let e=t.raw?t.value:this.source.slice(t.pos,t.end),i=t.raw&&t.keywordEligible?void 0:this.source;return new Z(e,t.pos,t.end,i,i===void 0?te:vt(this.tok.getSubstitutionContext()),this.depth)}toWordFromPosEnd(t,e,i){return t.pos===e&&t.end===i?this.toWord(t):new Z(this.source.slice(e,i),e,i,this.source,vt(this.tok.getSubstitutionContext()),this.depth)}parseAssignment(t){let e=t.raw?t.value:this.source.slice(t.pos,t.end);return new se(e,t.pos,t.end,this.source,t.assignmentOperatorPos,this.depth,this.tok.getSubstitutionContext())}makeCompoundList(t){let e=this.tok.getPos(),i=t.length>0?t[0].pos:e,s=t.length>0?t[t.length-1].end:e;return{type:"CompoundList",pos:i,end:s,commands:t}}};var Cs=new Set(["CommandExpansion","ProcessSubstitution"]);function Me(r){if(Cs.has(r.type))return!0;let t=!1;return Mt(r,e=>{t||=Me(e)}),t}function Qe(r,t){if(r.type==="SimpleExpansion"||r.type==="ParameterExpansion"||r.type==="ArithmeticExpansion"){t.push(r.text);return}Mt(r,e=>Qe(e,t))}function xs(r){let t=[];if(Qe(r,t),t.length===0)return!1;let e=r.type==="Word"?r.value:r.text,i=e.replace(/(^|[=@])\$(?:HOME|\{HOME\})(?=\/|$)/,"$1"),s=i===e?0:1,o=0;for(let n of t)if(!/^\$\d+$/.test(n)){if((n==="$HOME"||n==="${HOME}")&&o<s){o++;continue}return!0}return i!==e&&/\$[A-Za-z_{@*#?!$-]/.test(i)}function As(r,t,e){for(let i of r)i.type==="HereDoc"?i.delimiter?.quoted?e.push(i.body.text):t.push(i.operator):(i.type==="HereString"||i.operator==="<"||i.operator==="<>"||i.operator==="<&")&&t.push(i.operator)}var gs=new Set(["sh","bash","zsh","dash","ksh"]),ks=new Set(["echo","printf"]),_s=new Set(["-m","--message","--body","-b","--title","-t","--notes","--subject"]),Es=r=>r.slice(r.lastIndexOf("/")+1);function Ue(r,t,e,i,s=[]){let o;if(r.type==="Command"&&r.name){let p=r,c=[p.name.value,...p.args.map(x=>x.type==="Word"?x.value:x.text)],d=Es(c[0]),l=c;if(ks.has(d))l=[c[0]];else{if(gs.has(d)){let x=c.findIndex((f,C)=>C>0&&/^-[a-zA-Z]*c[a-zA-Z]*$/.test(f));x>0&&c[x+1]!==void 0&&(o=c[x+1],l=c.filter((f,C)=>C!==x+1))}else d==="eval"&&(o=c.slice(1).join(" "));l=l.filter((x,f)=>f===0||!_s.has(l[f-1]))}let m=[],A=[];As([...s,...p.redirects],m,A);let k=p.args.filter(xs).map(x=>x.type==="Word"?x.value:x.text);e.push({argv:c,inSubst:t,text:l.join(" "),substArgs:[p.name,...p.args].some(Me),expandArgs:k,stdin:m,heredocs:A})}if(o!==void 0){if(i>8)throw new Error("shell nesting too deep");qe(o,t,e,i+1)}let n=t||r.type==="CommandExpansion",h=r.type==="Redirected"?[...s,...r.redirects]:r.type==="Command"?[]:s;Mt(r,p=>Ue(p,n,e,i,h))}function qe(r,t,e,i){let s=Fe(r);if(s.errors?.length)throw new Error("shell parse error");Ue(s,t,e,i)}function bs(r){try{let t=[];return qe(r,!1,t,0),t}catch{return null}}0&&(module.exports={commandInvocations});

    })(module, module.exports);
    return module.exports.commandInvocations(cmd);
  } catch (err) {
    return null;
  }
})();
const onInvocations = (fn) => (c) => (shellInvocations ? shellInvocations.some((i) => fn(i.text)) : fn(c));
const { execFileSync } = require('child_process');
const path = require('path');

function shellWords(s) {
  return (s.match(/(?:'[^']*'|"[^"]*"|[^\s'"])+/g) || []).map((w) => w.replace(/['"\\]/g, ''));
}

const gitCache = new Map();
function gitOut(dir, args) {
  const key = dir + '\0' + args.join('\0');
  if (gitCache.has(key)) return gitCache.get(key);
  try {
    const out = execFileSync('git', ['-C', dir, ...args], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
    gitCache.set(key, out);
    return out;
  } catch (err) {
    gitCache.set(key, '');
    return '';
  }
}
function resolveDefaultBranch(runGit) {
  let def = '';
  try { def = (runGit(['symbolic-ref', '--short', 'refs/remotes/origin/HEAD']) || '').trim(); } catch (e) {}
  return def;
}
function resolveBaseRef(runGit, headRef) {
  const def = resolveDefaultBranch(runGit);
  const candidates = [...new Set([def, 'origin/main', 'main', 'origin/master', 'master'].filter(Boolean))];
  for (const ref of candidates) {
    try {
      const mb = (runGit(['merge-base', headRef || 'HEAD', ref]) || '').trim();
      if (mb) return mb;
    } catch (e) {}
  }
  return '';
}

// Set by pushesProtectedBranch. A src:dst refspec, a URL or a remote other than
// origin can send an allowed feat/* push somewhere else, and no warning-free Bash
// rule can match a colon then anything.
let retargetedPush = false;

// Without this, a line continuation or a separator inside quotes cuts a push's
// later arguments off from it. A best guess: an apostrophe in a comment pairs
// with a later quote, and "\\" before a newline isn't a continuation, so the
// checks also read the raw command.
function joinPushText(command) {
  return command
    .replace(/\\\n/g, '')
    .replace(/\\[\s\S]|'[^']*'|"(?:\\[\s\S]|[^"\\])*"/g, (quoted) => quoted.replace(/[;&|()\n]/g, ' '));
}

// A statement keeps its pipes, so a flag piped in through xargs counts here. A
// flag anywhere in the statement counts, so "git push x | grep -f y" misfires,
// and so does a $ or backtick after the push, which can expand to a flag or a
// target, so "| tee $LOG" misfires too. A flag in another statement is
// flagCanReachPush's job. A misfire costs a retry, and a miss can rewrite main.
// Quantifiers are bounded so long input stays well inside the hook's timeout.
function stripHeredocs(text) {
  if (!text.includes('<<')) return text;
  return text.replace(/(?:^|[;&|\n])\s*(?:cat|tee)\b[^;&|\n]*<<-?\s*['"](\w+)['"][^\n]*\n[\s\S]*?\n\1(?=\r?\n|$)/g, '');
}

const PUSH_COMMAND = /git\b(?:\s+-\S+(?:\s+[^\s-]\S*)?){0,16}\s+push/;
function hasPush(text) {
  const s = stripHeredocs(text).replace(/\\\r?\n/g, ' ').replace(/['"\\]/g, '');
  if (!/\bpush|\b(?:remote|branch|alias)\./i.test(s)) return false;
  return PUSH_COMMAND.test(s) || /\b(?:-c\s+|config\b[^;&|\n]{0,120}\s)(?:remote|push|branch|alias)\./i.test(s);
}
function hasMergeOrGate(text) {
  const s = stripHeredocs(text).replace(/\\\r?\n/g, ' ').replace(/['"\\]/g, '');
  if (!/\b(?:merge|edit|api)\b/.test(s)) return false;
  return /\b(?:pr\s+merge|(?:issue|pr)\s+edit|gh\s+api)\b/.test(s);
}
function forcesPush(text) {
  text = stripHeredocs(text);
  if (hasPush(text)) {
    if (/(?<![-\w])[A-Za-z_]\w*=['"][^'"]*\bgit\b[^'"]*\bpush\b/.test(text)) return true;
    if (/(?:^|[;&|\n])\s*(?:function\s+\w+|\w+\s*\(\s*\))\s*[{(]/.test(text)) return true;
    if (/\bGIT_CONFIG_(?:COUNT|PARAMETERS|KEY_|VALUE_|GLOBAL|SYSTEM)\b|--config-env\b/.test(text)) return true;
    if (/\b(?:-c\s+|config\b[^;&|\n]{0,120}\s)['"]?(?:remote|push|branch|alias)\./i.test(text)) return true;
  }
  for (const raw of text.split(/&&|\|\||;|\n/)) {
    const statement = raw.replace(/['"\\]/g, '');
    const push = PUSH_COMMAND.exec(statement);
    if (!push) continue;
    if (/(?:^|\s)(?:--(?:fo(?:r(?:c(?:e)?)?)?|al(?:l)?|m(?:i(?:r(?:r(?:o(?:r)?)?)?)?)?|b(?:r(?:a(?:n(?:c(?:h(?:e(?:s)?)?)?)?)?)?)?)\b|-[a-eg-zA-Z0-9]*f|-[a-zA-Z0-9]*[*?[\]])/.test(statement)) return true;
    if (/[$\x60]/.test(statement.slice(push.index + push[0].length))) return true;
  }
  return flagCanReachPush(text);
}

// The row #94 removed blocked any command holding a git push and a flag. That
// still holds unless the command has no $, backtick, backslash, #, (), {}, redirect or heredoc, no
// statement holds two pushes, and every part between ; & | && || or a newline is a
// plain "git push …", or rm, grep, pkill, gh label, git worktree remove, or gh api whose
// first word after unquoted -X, -f, -F or -H value pairs is a repo's issues path;
// any other flag before it blocks, since gh may read the path as a value. Anything else blocks, including cd,
// a subshell, a process substitution, a shell function, a variable
// assignment and a commit message holding a flag. Listing the safe parts, not the
// unsafe ones, because each list of unsafe forms missed some.
function flagCanReachPush(text) {
  const oldFlag = /(?:^|\s)(?:--force\b|-f\b)/;
  const plain = text.replace(/['"\\]/g, '');
  if (!PUSH_COMMAND.test(plain) || !(oldFlag.test(plain) || oldFlag.test(text))) return false;
  if (/[$\x60\\#(){}]|<</.test(text)) return true;
  const pushCount = (s) => (s.replace(/['"]/g, '').match(new RegExp(PUSH_COMMAND, 'g')) || []).length;
  if (text.split(/&&|\|\||;|\n/).some((statement) => pushCount(statement) > 1)) return true;
  for (const rawPart of text.replace(/(git\s+push\b[^;&|\n]*?)\s*\b2>&1\b/g, '$1').replace(/&>/g, '2>').split(/[;&|\n]/)) {
    const part = pushCount(rawPart) ? rawPart.replace(/\b2>\s*\/dev\/null\b/g, '') : rawPart;
    if (!part.trim()) continue;
    if (/[<>]/.test(part)) return true;
    if (pushCount(part) ? !/^\s*git\s+push(?:\s|$)/.test(part)
      : !/^\s*(?:(?:rm|grep|pkill|gh\s+label|git\s+worktree\s+remove)\s|gh\s+api\s+(?:(?:-[XfFH]|--(?:method|field|raw-field|header))\s+\w[\w.=:-]*\s+)*(?!\S*\.\.)repos\/[\w.-]+\/[\w.-]+\/issues(?:\/\w+)*(?:\s|$))/.test(part)) return true;
  }
  return false;
}

// True when a git push in the command could land on main/master, push every
// branch, force-update a ref, or target a branch it can't resolve ($VAR).
// isQuotedText: this is the inside of a quoted string, often prose, so it asks only
// on a refspec, as a prompt can stall an unattended run.
const MAX_CMD_LENGTH = 8192;
const MAX_SEGMENTS = 64;
function pushesProtectedBranch(command, cwd, isQuotedText = false) {
  command = stripHeredocs(command);
  if (!hasPush(command)) return false;
  if (command.length > MAX_CMD_LENGTH) return true;
  const joined = joinPushText(command);
  if (forcesPush(joined) || forcesPush(command)) return true;
  const quoted = [...command.matchAll(/'([^']*)'|"([^"]*)"/g)].map((m) => m[1] ?? m[2]);
  if (quoted.some((q) => /\bgit\b/.test(q) && pushesProtectedBranch(q, cwd, true))) return true;
  // Redirects go first: "2>&1" isn't a separator, and a redirect target isn't a refspec.
  const withoutRedirects = joined.replace(/&>>?/g, '>').replace(/(?<!\d)\d*[<>]+&?\s*[^\s;&|()\x60]*/g, ' ');
  const joinedSegments = withoutRedirects.split(/&&|\|\||[;|&\n()\x60]/);
  const rawSegments = command.split(/&&|\|\||[;|&\n()]/);
  if (joinedSegments.filter((s) => PUSH_COMMAND.test(s.replace(/['"\\]/g, ''))).length > MAX_SEGMENTS) return true;
  // Raw segments only block: split inside quotes, they are often prose, so they never ask.
  let walkDir = cwd;
  for (const [k, segment] of [...joinedSegments, ...rawSegments].entries()) {
    if (k === joinedSegments.length) walkDir = cwd;
    const w = shellWords(segment);
    if (w[0] === 'cd') {
      const cdTarget = w.slice(1).find((x) => !x.startsWith('-'));
      walkDir = cdTarget ? path.resolve(walkDir, cdTarget) : (process.env.HOME || '/');
      continue;
    }
    for (let g = 0; g < w.length; g++) {
      if (!(w[g] === 'git' || w[g].endsWith('/git'))) continue;
      let i = g + 1;
      let dir = walkDir;
      for (; i < w.length && w[i].startsWith('-'); i++) {
        if (w[i] === '-C') dir = path.resolve(dir, w[++i] || '.');
        else if (w[i] === '-c') {
          if (/^(?:remote|push|branch|alias)\./i.test(w[++i] || '')) return true;
        } else if (/^--config-env(?:=|$)/.test(w[i])) return true;
        else if (/^(--git-dir|--work-tree|--namespace|--exec-path)$/.test(w[i])) i++;
      }
      if (w[i] !== 'push') continue;
      const positional = [];
      for (let j = i + 1; j < w.length; j++) {
        const a = w[j];
        if (/[{}]/.test(a)) return true;
        if (/^(-o|--push-option|--repo|--receive-pack|--exec)$/.test(a)) j++;
        else if (/^--(?:al(?:l)?|m(?:i(?:r(?:r(?:o(?:r)?)?)?)?)?|b(?:r(?:a(?:n(?:c(?:h(?:e(?:s)?)?)?)?)?)?)?|fo(?:r(?:c(?:e)?)?)?)$/.test(a) || /^--force/.test(a) || /^-[a-zA-Z0-9]*f/.test(a) || /^-[a-zA-Z0-9]*[*?[\]]/.test(a)) return true;
        else if (!a.startsWith('-')) positional.push(a);
      }
      const args = w.slice(i + 1);
      // A positional remote overrides --repo, so the first positional is never a refspec.
      const refspecs = positional.slice(1);
      if (refspecs.some((r) => r.startsWith('+'))) return true;
      const otherRemote = args.some((a) => /^--repo\b/.test(a)) || (positional.length && positional[0] !== 'origin');
      const asks = (isQuotedText ? refspecs : args).some((a) => a.includes(':')) || (!isQuotedText && otherRemote);
      if (k < joinedSegments.length && asks) retargetedPush = true;
      const current = gitOut(dir, ['symbolic-ref', '--short', '-q', 'HEAD']);
      if (!refspecs.length) {
        // A bare push is safe only from a feature branch tracking its own name.
        const upstream = gitOut(dir, ['rev-parse', '--abbrev-ref', '--symbolic-full-name', '@{u}']).replace(/^[^/]+\//, '');
        if (!current || /^(main|master)$/.test(current) || upstream !== current) return true;
        continue;
      }
      const targets = refspecs.map((r) => r.slice(r.lastIndexOf(':') + 1)).map((t) => (t === 'HEAD' || t === '@' ? current || 'main' : t));
      if (targets.some((t) => /^(?:(?:refs\/)?heads\/|refs\/)?(main|master)$/.test(t) || /[$\x60{}]/.test(t))) return true;
    }
  }
  return false;
}

function addsGatedLabel(c) {
  if (!/ready|approved-to-start/.test(c.replace(/['"\\]/g, ''))) return false;
  const s = joinPushText(c).replace(/['"\\]/g, '');
  if (/\b(?:issue|pr)\s+edit\b[^;&|\n]*--add-label[\s=]+(?:\S*,)?(?:ready|approved-to-start)(?:,|\s|$)/.test(s)) return true;
  if (/\bgh\b[^;&|\n]*\bapi\b[^;&|\n]*(?:issues\/[^/\s]+\/labels\b|addLabelsToLabelable)[^;&|\n]*\b(?:ready|approved-to-start)\b/.test(s)) return true;
  return false;
}

function removesGatedLabel(c) {
  const raw = c.replace(/['"\\]/g, '');
  if (!/needs-human-approval|issues\/|Labels/.test(raw)) return false;
  const s = joinPushText(c).replace(/['"\\]/g, '');
  if (/\b(?:issue|pr)\s+edit\b[^;&|\n]*--remove-label[\s=]+(?:\S*,)?needs-human-approval(?:,|\s|$)/.test(s)) return true;
  if (/\bgh\b[^;&|\n]*\bapi\b[^;&|\n]*(?:issues\/[^/\s]+\/labels\b|removeLabelsFromLabelable)[^;&|\n]*\bneeds-human-approval\b/.test(s)) return true;
  for (const stmt of s.split(/[;&|\n]/)) {
    if (!/\bgh\b[^;&|\n]*\bapi\b/.test(stmt)) continue;
    if (/\b(?:addLabelsToLabelable|removeLabelsFromLabelable|clearLabelsFromLabelable)\b/.test(stmt)) return true;
    const isMutating = /(?:^|\s)(?:-(?:X\s*(?:PUT|PATCH|DELETE|POST)|[fF])\b|--(?:method[\s=]+(?:PUT|PATCH|DELETE|POST)|(?:field|raw-field|input)\b))/i.test(stmt);
    if (!isMutating) continue;
    // Deleting one other named label (e.g. in-progress) can't drop the gate; names are case-insensitive, encoded ones stay blocked.
    const named = stmt.match(/(?:^|[\s/])issues\/[^/\s]+\/labels\/([A-Za-z0-9._-]+)(?:\s|$)/);
    const otherLabel = named && named[1].toLowerCase() !== 'needs-human-approval';
    if (!otherLabel && /(?:^|[\s/])issues\/[^/\s]+\/labels(?:\/[^/\s?#]+)?(?:[?#]\S*)?(?:\s|$)/.test(stmt)) return true;
    if (/(?:^|[\s/])issues\/[^/\s]+(?:[?#]\S*)?(?:\s|$)/.test(stmt) && /(?:\blabels\b|--input\b)/i.test(stmt)) return true;
  }
  return false;
}

let mergeRefusal = '';
function lacksOriginMain(command, cwd) {
  mergeRefusal = '';
  if (!/\bpr\s+merge\b/.test(command.replace(/\\\r?\n/g, ' ').replace(/['"\\]/g, ''))) return false;
  const masked = joinPushText(command);
  for (const segment of masked.split(/&&|\|\||[;|&\n()]/)) {
    const w = shellWords(segment);
    const start = w.findIndex((x) => !/^\w+=/.test(x));
    if (start < 0 || !(w[start] === 'gh' || w[start].endsWith('/gh'))) continue;
    let i = start + 1;
    let repoFlag = [];
    let repoSlug = '';
    for (; i < w.length && w[i].startsWith('-'); i++) {
      if (w[i] === '-R' || w[i] === '--repo') { repoSlug = w[++i] || ''; repoFlag = ['-R', repoSlug]; }
      else if (/^--repo=/.test(w[i])) { repoSlug = w[i].slice(7); repoFlag = [w[i]]; }
    }
    if (w[i] !== 'pr' || w[i + 1] !== 'merge') continue;
    const rest = w.slice(i + 2);
    let prArg = '';
    for (let j = 0; j < rest.length; j++) {
      const a = rest[j];
      if (a === '-R' || a === '--repo') { repoSlug = rest[++j] || ''; repoFlag = ['-R', repoSlug]; continue; }
      if (/^--repo=/.test(a)) { repoSlug = a.slice(7); repoFlag = [a]; continue; }
      if (/^(--body|-b|--body-file|-F|--subject|-t|--author-email|-A|--match-head-commit)$/.test(a)) { j++; continue; }
      if (a.startsWith('-')) continue;
      if (!prArg) prArg = a;
    }
    if (repoSlug) {
      const originUrl = gitOut(cwd, ['remote', 'get-url', 'origin']);
      const slugRe = new RegExp('[:/]' + repoSlug.replace(/[^\w./-]/g, '\\$&') + '(?:\\.git)?/?$', 'i');
      if (!originUrl || !slugRe.test(originUrl)) {
        mergeRefusal = 'gh pr merge blocked: -R ' + repoSlug + ' does not match cwd origin; run gh pr merge from a checkout of ' + repoSlug + '.';
        return true;
      }
    }
    let headSha = '';
    let headRef = '';
    try {
      const viewArgs = ['pr', 'view', ...(prArg ? [prArg] : []), ...repoFlag, '--json', 'headRefOid,headRefName'];
      const out = execFileSync('gh', viewArgs, { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] });
      const parsed = JSON.parse(out) || {};
      headSha = String(parsed.headRefOid || '').trim();
      headRef = String(parsed.headRefName || '').trim();
    } catch (err) {
      mergeRefusal = 'gh pr merge blocked: could not read PR headRefOid via gh pr view.';
      return true;
    }
    if (!headSha) {
      mergeRefusal = 'gh pr merge blocked: PR headRefOid is empty.';
      return true;
    }
    const rawDef = resolveDefaultBranch((args) => gitOut(cwd, args));
    const defBranch = (rawDef || '').replace(/^origin\//, '') || 'main';
    const remoteRef = 'origin/' + defBranch;
    try {
      execFileSync('git', ['-C', cwd, 'fetch', 'origin', defBranch], { stdio: ['ignore', 'ignore', 'ignore'] });
    } catch (err) {
      mergeRefusal = 'gh pr merge blocked: git fetch origin ' + defBranch + ' failed — ensure ' + remoteRef + ' is reachable before merging.';
      return true;
    }
    try {
      execFileSync('git', ['-C', cwd, 'cat-file', '-e', headSha + '^{commit}'], { stdio: ['ignore', 'ignore', 'ignore'] });
    } catch (err) {
      try {
        execFileSync('git', ['-C', cwd, 'fetch', 'origin', headRef || headSha], { stdio: ['ignore', 'ignore', 'ignore'] });
      } catch (fetchErr) {
        // merge-base below will report if the commit is still missing
      }
    }
    const mainSha = gitOut(cwd, ['rev-parse', remoteRef]);
    if (!mainSha) {
      mergeRefusal = 'gh pr merge blocked: cannot resolve ' + remoteRef + '.';
      return true;
    }
    try {
      execFileSync('git', ['-C', cwd, 'merge-base', '--is-ancestor', mainSha, headSha], { stdio: ['ignore', 'ignore', 'ignore'] });
    } catch (err) {
      mergeRefusal = 'gh pr merge blocked: PR head ' + headSha.slice(0, 12) + ' does not contain ' + remoteRef + ' (' + mainSha.slice(0, 12) + '). Merge ' + remoteRef + ' into the branch, re-verify, and push before merging.';
      return true;
    }
  }
  return false;
}
const ghOs = require('os');
const ghSecretPatterns = [
  new RegExp("(?:api[_-]?key|secret|password|token)\\s*[:=]\\s*['\"][A-Za-z0-9_\\-\\/+]{12,}['\"]", "i"),
  new RegExp("-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----", ""),
  new RegExp("AKIA[0-9A-Z]{16}", ""),
  new RegExp("\\bgh[pousr]_[A-Za-z0-9]{36}\\b", ""),
  new RegExp("\\bgithub_pat_[A-Za-z0-9_]{22,}\\b", ""),
  new RegExp("\\bsk-ant-[A-Za-z0-9_-]{20,}\\b", "")
];
let ghReason = '';
const GH_FILE_CAP = 1024 * 1024;
const GH_ENV_RE = /(?:^|[\s;&|(])(?:export\s+)?(GH_CONFIG_DIR|GH_HOST|GH_TOKEN|GITHUB_TOKEN|GH_ENTERPRISE_TOKEN|GH_REPO|GIT_SSH|GIT_SSH_COMMAND|GIT_CONFIG\w*|GIT_EXEC_PATH|GIT_DIR|GIT_WORK_TREE)=/g;
const GH_WRAPPERS = new Set(['env', 'command', 'exec', 'nohup', 'time', 'xargs', 'sudo']);

function ghOriginOwner(dir) {
  const m = /github\.com[:/]([^/:\s]+)\/[^/\s]+?(?:\.git)?\/?$/i.exec(gitOut(dir, ['remote', 'get-url', 'origin']));
  return m ? m[1].toLowerCase() : '';
}

// '' when the slug (OWNER/REPO or HOST/OWNER/REPO) is under cwd's origin owner, else why not.
function ghRepoProblem(slug, dir) {
  const parts = slug.split('/');
  if (parts.length === 3 && parts[0].toLowerCase() !== 'github.com') return 'host ' + parts[0] + ' is not github.com';
  const owner = (parts.length === 3 ? parts[1] : parts[0]).toLowerCase();
  const origin = ghOriginOwner(dir);
  if (!origin) return 'cwd has no parseable github.com origin remote';
  return owner === origin ? '' : 'owner ' + owner + ' differs from the origin owner ' + origin;
}

// Wrappers like env/xargs are skipped; null when this invocation is not gh or git.
function ghCommandArgv(inv) {
  const argv = inv.argv;
  let i = 0;
  while (i < argv.length && /^\w+=/.test(argv[i])) i++;
  while (i < argv.length && GH_WRAPPERS.has(path.basename(argv[i]))) {
    i++;
    while (i < argv.length && (argv[i].startsWith('-') || /^\w+=/.test(argv[i]))) i++;
  }
  const name = argv[i] ? path.basename(argv[i]) : '';
  return name === 'gh' || name === 'git' ? { name, args: argv.slice(i + 1), inv } : null;
}

function ghInvocations() {
  if (shellInvocations) return shellInvocations;
  // No AST: read redirects off the segment with quoted-delimiter heredocs removed and quotes blanked.
  return joinPushText(cmd).split(/&&|\|\||[;|&\n()]/).map((segment) => ({
    argv: shellWords(segment),
    substArgs: /\$\(|\x60|<\(/.test(segment),
    stdin: /<(?!\()/.test(segment.replace(/<<-?\s*(?:'[^']*'|"[^"]*"|\\\w+)/g, '').replace(/'[^']*'|"[^"]*"/g, '""')) ? ['<'] : [],
    heredocs: [],
  }));
}

function ghFileProblem(raw, flag, dir) {
  if (raw === '-') return '';
  if (!raw) return flag + ' has no literal path (xargs or a missing value)';
  const home = process.env.HOME || ghOs.homedir();
  const p = raw.replace(/^~(?=\/|$)/, () => home).replace(/^\$(?:HOME|\{HOME\})(?=\/|$)/, () => home);
  if (/[$\x60]/.test(p)) return flag + ' path ' + raw + ' contains a variable or substitution; pass a literal path';
  const abs = path.resolve(dir, p);
  try { if (fs.lstatSync(abs).isSymbolicLink()) return flag + ' path ' + raw + ' is a symbolic link; pass the real file'; } catch (err) { /* missing */ }
  const realOf = (r) => { try { return fs.realpathSync(r); } catch (err) { return path.join(realOf(path.dirname(r)), path.basename(r)); } };
  const real = realOf(abs);
  const segments = real.split(path.sep);
  const base = segments[segments.length - 1];
  if (segments.some((s) => ['.ssh', '.aws', '.gnupg', '.kube'].includes(s)) || base === '.env' || base.startsWith('.env.')) return flag + ' path ' + raw + ' is a credentials location';
  const roots = [gitOut(dir, ['rev-parse', '--show-toplevel']), ghOs.tmpdir(), process.env.TMPDIR || '', '/tmp', '/private/tmp'].filter(Boolean).map(realOf);
  if (!roots.some((r) => real === r || real.startsWith(r + path.sep))) return flag + ' path ' + raw + ' is outside the repository and the temp directory';
  let st;
  try { st = fs.statSync(real); } catch (err) { return ''; }
  if (!st.isFile()) return '';
  if (st.size > GH_FILE_CAP) return flag + ' file ' + raw + ' is over 1 MB and cannot be scanned for secrets';
  const text = fs.readFileSync(real, 'utf8');
  return ghSecretPatterns.some((re) => re.test(text)) ? flag + ' file ' + raw + ' contains a secret-shaped string' : '';
}

const GH_API_VALUE_FLAGS = new Set(['-X', '--method', '-H', '--header', '-q', '--jq', '-t', '--template', '--cache', '--hostname', '-f', '-F', '--field', '--raw-field', '--input', '-p', '--preview']);
const GH_INLINE_FLAGS = new Set(['--body', '-b', '--title', '-t', '--notes', '-n']);
const GH_FILE_FLAGS = new Set(['--body-file', '--notes-file', '--input']);

function ghProblem(name, args, dir, inv) {
  const isApi = name === 'gh' && args[0] === 'api';
  const scan = (v, flag) => (ghSecretPatterns.some((re) => re.test(v)) ? flag + ' value looks like a secret' : '');
  const consumed = new Set();
  const expands = inv && Array.isArray(inv.expandArgs)
    ? inv.expandArgs[0]
    : args.find((a) => /\$[A-Za-z_{@*#?!$-]/.test(a.replace(/(^|[=@])\$(?:HOME|\{HOME\})(?=\/|$)/, '$1')));
  if (expands !== undefined) return 'Rule 4 (expansion): ' + expands.slice(0, 40) + ' holds a $ expansion, which the shell resolves before gh runs outside the sandbox, so the secret scan cannot see it. Put the text in a file inside the repo or $TMPDIR and pass --body-file <file>.';
  for (let i = 0; i < args.length; i++) {
    let a = args[i];
    if (a === '--') break;
    let value;
    const eq = a.startsWith('--') ? a.indexOf('=') : -1;
    // gh (cobra) also takes an attached short value: -Fpath, -F=path, -Rowner/repo.
    const short = /^-([FfbtRnp])=?(.+)$/.exec(a);
    if (eq > 0) { value = a.slice(eq + 1); a = a.slice(0, eq); }
    else if (short) { value = short[2]; a = '-' + short[1]; }
    else if (a.startsWith('-') && (GH_FILE_FLAGS.has(a) || GH_INLINE_FLAGS.has(a) || ['-F', '-f', '-R', '--repo', '--field', '--raw-field', '--hostname'].includes(a) || (isApi && GH_API_VALUE_FLAGS.has(a)))) { value = args[i + 1] || ''; consumed.add(i + 1); i++; }
    if (value === undefined) continue;
    let bad = '';
    if (a === '-R' || a === '--repo') {
      const why = ghRepoProblem(value, dir);
      if (why) return 'Rule 3 (destination): ' + a + ' ' + value + ': ' + why + '. Run gh from a checkout of that repo, or use a repo under the origin owner.';
    } else if (a === '--hostname') {
      if (value.toLowerCase() !== 'github.com') return 'Rule 3 (destination): --hostname ' + value + ' is not github.com. Only github.com is allowed.';
    } else if (GH_FILE_FLAGS.has(a) || (a === '-F' && !isApi)) {
      bad = ghFileProblem(value, a, dir);
    } else if (isApi && (a === '-F' || a === '--field') && value.includes('=')) {
      const v = value.slice(value.indexOf('=') + 1);
      bad = v.startsWith('@') ? ghFileProblem(v.slice(1), a + ' key=@file', dir) : scan(v, a);
    } else if (isApi && (a === '-f' || a === '--raw-field') && value.includes('=')) {
      bad = scan(value.slice(value.indexOf('=') + 1), a);
    } else if (GH_INLINE_FLAGS.has(a) && !(isApi && a === '-t')) {
      bad = scan(value, a);
    }
    if (bad) return 'Rule ' + (/secret/.test(bad) ? '2 (secret content)' : '1 (file upload)') + ': ' + bad + '. Write the body to a file inside the repo or $TMPDIR without secrets and pass --body-file <file>.';
  }
  // gh release create|upload <tag> <asset>...: assets are positional file uploads (a #label suffix is allowed).
  if (name === 'gh' && args[0] === 'release' && /^(create|upload)$/.test(args[1] || '')) {
    const positional = args.slice(2).filter((a, i) => !a.startsWith('-') && !consumed.has(i + 2));
    for (const asset of positional.slice(1)) {
      const bad = ghFileProblem(asset.replace(/#.*$/, ''), 'release asset', dir);
      if (bad) return 'Rule 1 (file upload): ' + bad + '. Upload only build outputs inside the repo.';
    }
  }
  if (!isApi) {
    for (let i = 0; i < args.length; i++) {
      const a = args[i];
      if (a.startsWith('-') || consumed.has(i)) continue;
      const url = /^(https?:\/\/)?([^/\s]+)\/([^/\s]+)\/([^/\s]+)\/(?:issues|pull)\//i.exec(a);
      if (url && (url[1] || url[2].includes('.'))) {
        const host = url[2].toLowerCase() === 'www.github.com' ? 'github.com' : url[2];
        const why = ghRepoProblem(host + '/' + url[3] + '/' + url[4], dir);
        if (why) return 'Rule 3 (destination): ' + a + ': ' + why + '. Use the issue or PR number from a checkout of that repo.';
      } else {
        const other = /^https?:\/\/([^/]+)/i.exec(a);
        if (other && !['github.com', 'www.github.com', 'api.github.com'].includes(other[1].toLowerCase())) return 'Rule 3 (destination): ' + other[1] + ' is not a GitHub host and is blocked.';
      }
    }
  }
  if (isApi) {
    for (let i = 1; i < args.length; i++) {
      const a = args[i];
      if (a.startsWith('-') || consumed.has(i)) continue;
      const url = /^https?:\/\/([^/]+)/i.exec(a);
      if (url && !['api.github.com', 'github.com'].includes(url[1].toLowerCase())) return 'Rule 3 (destination): gh api to ' + url[1] + ' is blocked. Only api.github.com is allowed.';
      const repo = /^\/?repos\/([^/]+)\//.exec(a);
      // {owner}/:owner is filled by gh from the same base repo a command without -R uses, so it adds no destination.
      if (repo && !/^(?:\{owner\}|:owner)$/.test(repo[1])) {
        const origin = ghOriginOwner(dir);
        // no parseable origin: nothing to compare with, so only -R/--repo/GH_REPO fail closed
        if (origin && repo[1].toLowerCase() !== origin) return 'Rule 3 (destination): gh api repos/' + repo[1] + '/... is not under the origin owner ' + origin + '. Run it from a checkout of that repo.';
      }
    }
  }
  return '';
}

function ghGuardCheck(c, dir) {
  const calls = ghInvocations().map(ghCommandArgv).filter(Boolean);
  if (!calls.length) return '';
  const unquoted = c.replace(/'[^']*'|"(?:\\[\s\S]|[^"\\])*"/g, '""');
  for (const m of unquoted.matchAll(GH_ENV_RE)) {
    if (m[1] === 'GH_REPO') {
      const v = /GH_REPO=(?:'([^']*)'|"([^"]*)"|([^\s;&|)]*))/.exec(c);
      const slug = v ? (v[1] ?? v[2] ?? v[3]) : '';
      const why = ghRepoProblem(slug, dir);
      if (!why) continue;
      return 'Rule 3 (destination): GH_REPO=' + slug + ': ' + why + '. Run gh from a checkout of that repo.';
    }
    return 'Rule 5 (env prefix): ' + m[1] + '= before gh or git can redirect it to another account, host or command and is blocked. Run the command without it.';
  }
  // AST: a real substitution node (a backtick inside single quotes is literal); no AST: the regex, never weaker.
  if (calls.some((x) => x.name === 'gh' && (shellInvocations ? x.inv.substArgs : /\$\(|\x60|<\(/.test(c)))) {
    return 'Rule 4 (substitution): $(...), backticks or <(...) inside a gh command are blocked, because gh runs outside the sandbox. Run the inner command first, write its output to a file, and pass --body-file <file>.';
  }
  for (const { name, args, inv } of calls) {
    if (name === 'gh') {
      if (inv.stdin.length) return 'Rule 1 (file upload): an input redirect (' + inv.stdin[0] + ') on a gh command lets the shell read any file or variable into gh outside the sandbox. Write the text to a file inside the repo or $TMPDIR and pass --body-file <file>, or use a quoted heredoc (<<' + "'EOF'" + ').';
      if (inv.heredocs.some((h) => ghSecretPatterns.some((re) => re.test(h)))) return 'Rule 2 (secret content): the heredoc body looks like a secret. Remove it before posting.';
    }
    if (name === 'git') {
      let i = 0;
      for (; i < args.length && args[i].startsWith('-'); i++) {
        if (/^(-C|--git-dir|--work-tree|--namespace|--exec-path)$/.test(args[i])) i++;
        else if (args[i] === '-c' || /^-c./.test(args[i]) || /^--config-env(?:=|$)/.test(args[i])) {
          if (/^(?:fetch|pull|push)$/.test(args.slice(i + 1).find((x) => !x.startsWith('-') && !/^[\w.-]+=/.test(x)) || '')) return 'Rule 5 (git config): git -c / --config-env on fetch, pull or push can run arbitrary commands outside the sandbox and is blocked. Set the option in the repo config beforehand, or drop it.';
        }
      }
      const sub = args.slice(i).find((x) => !x.startsWith('-'));
      if (/^(?:fetch|pull|push)$/.test(sub || '') && process.env.CLAUDE_PROJECT_DIR) {
        // A repo the agent created inside the project can carry its own core.sshCommand or hooks, and fetch/push run unsandboxed.
        // The git common dir is shared by a repo and its worktrees (.claude/worktrees/*) but not by a nested repo, so compare that.
        const commonOf = (d) => { const o = gitOut(d, ['rev-parse', '--git-common-dir']); try { return o ? fs.realpathSync(path.resolve(d, o)) : ''; } catch (err) { return ''; } };
        const here = commonOf(dir);
        const home = commonOf(process.env.CLAUDE_PROJECT_DIR);
        if (here && home && here !== home) return 'Rule 5 (nested repo): git ' + sub + ' from a repository other than the project (' + process.env.CLAUDE_PROJECT_DIR + ') runs unsandboxed with that repo\'s own config and hooks and is blocked. Run it from the project or one of its worktrees.';
      }
      continue;
    }
    const why = ghProblem(name, args, dir, inv);
    if (why) return why;
  }
  return '';
}

const STRICT_DISCARD = false;
let dirtyDiscards = [];
function discardsDirtyPaths(command, cwd) {
  dirtyDiscards = [];
  if (!/\b(?:checkout|restore)\b/.test(command)) return false;
  let walkDir = cwd;
  let broadHit = false;
  for (const segment of joinPushText(stripHeredocs(command)).split(/&&|\|\||[;|&\n()]/)) {
    const w = shellWords(segment);
    if (w[0] === 'cd') {
      const target = w.slice(1).find((x) => !x.startsWith('-'));
      walkDir = target ? path.resolve(walkDir, target) : (process.env.HOME || '/');
      continue;
    }
    const g = w.findIndex((x) => x === 'git' || x.endsWith('/git'));
    if (g < 0) continue;
    let i = g + 1;
    let dir = walkDir;
    for (; i < w.length && w[i].startsWith('-'); i++) {
      if (w[i] === '-C') dir = path.resolve(dir, w[++i] || '.');
      else if (/^(-c|--git-dir|--work-tree|--namespace|--exec-path)$/.test(w[i])) i++;
    }
    const sub = w[i];
    if (sub !== 'checkout' && sub !== 'restore') continue;
    const rest = w.slice(i + 1);
    const dash = rest.indexOf('--');
    const opts = dash < 0 ? rest : rest.slice(0, dash);
    if (sub === 'checkout' && opts.some((a) => /^(-[bB]|--orphan|--detach)$/.test(a))) continue;
    if (sub === 'restore' && opts.some((a) => /^(--staged|-S)$/.test(a)) && !opts.some((a) => /^(--worktree|-W)$/.test(a))) continue;
    const positional = [];
    for (let j = 0; j < opts.length; j++) {
      if (/^(-s|--source|--conflict|--pathspec-from-file)$/.test(opts[j])) j++;
      else if (!opts[j].startsWith('-')) positional.push(opts[j]);
    }
    let paths = dash < 0 ? positional : rest.slice(dash + 1);
    // Without --, a first word that names a commit is the ref (or a branch switch).
    if (sub === 'checkout' && dash < 0 && positional.length && gitOut(dir, ['rev-parse', '--verify', '-q', positional[0] + '^{commit}'])) paths = positional.slice(1);
    if (!paths.length) continue;
    const dirty = gitOut(dir, ['diff', '--name-only', 'HEAD', '--', ...paths]);
    if (dirty) {
      dirtyDiscards.push(...dirty.split('\n').filter(Boolean));
      const hasBroad = paths.some((p) => {
        if (p === '.' || p === './' || p === ':' || p === ':/' || /[*?[\]]/.test(p)) return true;
        try { return fs.statSync(path.resolve(dir, p)).isDirectory(); } catch (e) { return false; }
      });
      if (hasBroad) broadHit = true;
    }
  }
  if (!dirtyDiscards.length) return false;
  return STRICT_DISCARD || broadHit || new Set(dirtyDiscards).size > 1;
}

function hasPrettierConfig(dir) {
  for (;;) {
    let names = [];
    try { names = fs.readdirSync(dir); } catch (e) {}
    if (names.some((n) => n.startsWith('.prettierrc') || n.startsWith('prettier.config.'))) return true;
    if (names.includes('package.json')) {
      try {
        if ('prettier' in JSON.parse(fs.readFileSync(path.join(dir, 'package.json'), 'utf8'))) return true;
      } catch (e) {}
    }
    const parent = path.dirname(dir);
    if (parent === dir) return false;
    dir = parent;
  }
}

function unconfiguredPrettierTargets(command, cwd) {
  if (!command.includes('prettier')) return [];
  const bad = [];
  let dir = cwd;
  for (const segment of command.split(/&&|\|\||[;|&\n()]/)) {
    const w = shellWords(segment);
    if (w[0] === 'cd') { dir = path.resolve(dir, w[1] || '.'); continue; }
    const i = w.findIndex((x) => x === 'prettier' || x.endsWith('/prettier') || /^prettier@/.test(x));
    if (i < 0) continue;
    const args = w.slice(i + 1);
    if (!args.some((a) => a === '--write' || a === '-w')) continue;
    if (args.some((a) => /^--(config|find-config-path)\b/.test(a))) continue;
    for (let j = 0; j < args.length; j++) {
      const a = args[j];
      if (/^--(ignore-path|plugin|parser|log-level|cache-location|cache-strategy|stdin-filepath|config-precedence)$/.test(a)) { j++; continue; }
      if (a.startsWith('-')) continue;
      const literal = a.split(/[*?{[]/)[0];
      let target = path.resolve(dir, literal || '.');
      try { if (!fs.statSync(target).isDirectory()) target = path.dirname(target); } catch (e) { target = path.dirname(target); }
      if (!hasPrettierConfig(target)) bad.push(a);
    }
  }
  return bad;
}

let prettierTargets = [];

// Claude Code's excludedCommands only unsandbox a call whose every command matches
// (no pipe to jq, cd, file redirect), so a mixed gh call runs under Seatbelt and
// fails TLS with an error that does not point at the cause.
function claudeSandboxOn() {
  let on = false;
  for (const f of ['settings.json', 'settings.local.json']) {
    try {
      const s = JSON.parse(fs.readFileSync(path.join(process.env.CLAUDE_PROJECT_DIR || cwd, '.claude', f), 'utf8')).sandbox;
      if (s && typeof s.enabled === 'boolean') on = s.enabled;
    } catch (err) { /* absent */ }
  }
  return on;
}
// Mirrors the git/gh entries of excludedCommands in claude-sandbox.ts.
function unsandboxedGit(a) {
  if (a[0] === 'gh') return true;
  if (a[0] !== 'git') return false;
  return /^(?:fetch|pull|push)$/.test(a[1] || '') || (a[1] === 'branch' && /^(?:-d|-D|--delete|-u|--set-upstream-to|--unset-upstream)$/.test(a[2] || ''));
}
function ghStaysSandboxed(c) {
  const invs = ghInvocations().filter((x) => x.argv.length);
  if (!invs.some((x) => unsandboxedGit(x.argv) || path.basename(x.argv[0]) === 'gh')) return false;
  const redirect = /(?:^|[^<])>(?!&)/.test(stripHeredocs(c).replace(/'[^']*'|"(?:\\[\s\S]|[^"\\])*"/g, '""'));
  return (redirect || !invs.every((x) => unsandboxedGit(x.argv))) && claudeSandboxOn();
}

const blockedPatterns = [
  { test: (c) => c.length > MAX_CMD_LENGTH && (hasPush(c) || hasMergeOrGate(c)), reason: 'Command exceeds maximum length (8192 bytes) and cannot be safely inspected.' },
  { test: (c) => /\.fediim\/approvals(?:\/|\b)/i.test(stripHeredocs(c).replace(/['"\\]/g, '')), reason: 'Write-protection: Writes to ' + ".fediim/approvals/" + ' are blocked.' },
  { test: onInvocations((c) => {
    if (!/\brm\b/.test(c)) return false;
    if (c.includes('rm -rf /')) return true;
    const w = shellWords(c);
    const start = w.findIndex((x) => !/^\w+=/.test(x));
    if (start < 0 || path.basename(w[start]) !== 'rm') return false;
    let recursive = false;
    const targets = [];
    for (let i = start + 1; i < w.length; i++) {
      const a = w[i];
      if (a === '--') { targets.push(...w.slice(i + 1)); break; }
      if (a === '--recursive') recursive = true;
      else if (a.startsWith('-') && !a.startsWith('--') && /[rR]/.test(a)) recursive = true;
      else if (!a.startsWith('-')) targets.push(a);
    }
    return recursive && targets.some((t) => t === '.' || t === './' || t === '*' || t.startsWith('/') || t === '~' || t.startsWith('~/') || /^\$\{?HOME\}?(?:\/|$)/.test(t) || t.split('/').includes('..'));
  }), reason: 'Destructive command: rm -rf / (or home/parent/dot) is blocked.' },
  { test: (c) => /\bDROP\s+(TABLE|DATABASE)\b/i.test(c), reason: 'Destructive command: DROP TABLE/DATABASE is blocked.' },
  { test: onInvocations((c) => c.includes('kubectl delete')), reason: 'Destructive command: kubectl delete is blocked.' },
  { test: onInvocations((c) => c.includes('terraform destroy')), reason: 'Destructive command: terraform destroy is blocked.' },
  { test: onInvocations((c) => c.includes('--dangerously-skip-permissions')), reason: 'Destructive flag --dangerously-skip-permissions is blocked.' },
  { test: onInvocations((c) => /git\s+reset\s+--hard/.test(c)), reason: 'Destructive command: git reset --hard is blocked.' },
  { test: () => !!shellInvocations && shellInvocations.some((i) => i.inSubst && /^(?:\S*\/)?rm\s(?:.*\s)?(?:-[a-zA-Z]*[rR][a-zA-Z]*|--recursive)(?:\s|$)/.test(i.text)), reason: 'Destructive command: rm -r inside $(...) or backticks is blocked. Run the removal as its own command.' },
  { test: (c) => discardsDirtyPaths(c, cwd), get reason() { return 'git checkout/restore would discard uncommitted changes in ' + dirtyDiscards.join(', ') + '. Commit them first (a WIP commit is fine), then retry.'; } },
  { test: (c) => /\$\([^)]{0,400}<<(?!<)/.test(c), reason: 'Heredoc inside $(...) is blocked: bash 3.2 misparses it, so quotes or ")" in the body can break the command, change the text, or run a substitution. Write the text to a file and pass gh --body-file <file> or git commit -F <file> (or pipe the heredoc: git commit -F - <<EOF).' },
  { test: (c) => !!(ghReason = ghGuardCheck(c, cwd)), get reason() { return ghReason; } },
  { test: (c) => ghStaysSandboxed(c), reason: 'Sandbox: gh, git fetch/pull/push and git branch -d/-D/-u/--set-upstream-to/--unset-upstream only run outside the sandbox when every command in the call is one of them, with no pipe, cd, redirect or other command. This call would run sandboxed, where gh fails TLS verification (x509 OSStatus -26276) and git cannot write .git/config (could not lock config file). Run them as their own call: filter with --json/--jq instead of | jq, run from the repo (or pass gh -R OWNER/REPO) instead of cd, commit in one call and push in the next, and chain them only with each other via &&.' },
  { test: onInvocations((c) => /git\s+rebase/.test(c)), reason: 'Destructive command: git rebase is blocked.' },
  { test: onInvocations((c) => /git\s+(filter-branch|filter-repo)/.test(c)), reason: 'Destructive command: git filter-branch/repo is blocked.' },
  { test: (c) => pushesProtectedBranch(c, cwd), reason: 'Destructive command: git push to main/master, --all/--mirror, a force push, an unresolved ($VAR) target, or a $ or backtick anywhere in the push is blocked. Push a feature branch by name, with no substitution.' },
  { test: (c) => (prettierTargets = unconfiguredPrettierTargets(c, cwd)).length > 0, get reason() { return 'prettier --write on ' + prettierTargets.join(', ') + ': no Prettier config above it, so it would be reformatted with Prettier defaults. Format only paths with a config, or use prettier --check.'; } },
  { test: (c) => /\bpr\s+merge\b/.test(c.replace(/\\\r?\n/g, ' ').replace(/['"\\]/g, '')) && /\bgh\b[^;&|\n]*\bpr\s+merge\b[^;&|\n]*--(admin)\b/.test(joinPushText(c).replace(/['"\\]/g, '')), reason: 'Destructive command: gh pr merge with admin is blocked.' },
  { test: (c) => lacksOriginMain(c, cwd), get reason() { return mergeRefusal; } },
  { test: (c) => addsGatedLabel(c), reason: 'Human-only gate: the operator applies the ready and approved-to-start labels. Ask the operator to run the gh command themselves, then wait. Do not retry, inspect this hook, or work around it.' },
  { test: (c) => removesGatedLabel(c), reason: 'Human-only gate: only the operator removes needs-human-approval. Ask the operator, then wait. Do not retry, inspect this hook, or work around it.' }
];

for (const pattern of blockedPatterns) {
  if (pattern.test(cmd)) {
    console.error(pattern.reason);
    process.exit(2);
  }
}
if (retargetedPush) {
  console.log(JSON.stringify({ hookSpecificOutput: { hookEventName: 'PreToolUse', permissionDecision: 'ask', permissionDecisionReason: 'git push with a colon in its arguments or a remote other than origin can send a feature-branch push somewhere else.' } }));
}
process.exit(0);

},
(require, process, console) => {
// Blocks two Gradle mistakes in Bash commands: chaining another command after a
// piped Gradle call, which loses Gradle's exit code unless pipefail is set, and
// an install task with no ANDROID_SERIAL, which installs on every connected
// device. It stops mistakes and isn't a security boundary, so it fails open.
const fs = require('fs');

const LOST_EXIT_CODE_MESSAGE =
  "Gradle's exit code is lost in this pipe, so the next command runs even if the build failed. Redirect to a file and check the exit code, or set -o pipefail first and chain with &&.";
const UNSCOPED_INSTALL_MESSAGE =
  'Gradle installs on every connected device. Put ANDROID_SERIAL=<serial> in front of the Gradle call, or export it earlier in the same command.';
const STOP_DAEMON_MESSAGE =
  'Stopping the shared Gradle daemon kills builds in other sessions and worktrees. Pass --no-daemon to your own Gradle command instead.';

const HEREDOC_START = /(?<!<)<<(?!<)(-?)[ \t]*(?:'([^']*)'|"([^"]*)"|([^\s'"<>;&|()]+))/g;
// The lookbehind lets at most one double quote fail to close, which keeps this linear on a long run of escaped quotes.
const QUOTED_TEXT = /'[^']*'|(?<!\\)"(?:[^"\\]|\\[\s\S])*"/g;
const QUOTED_MARKER = 'QUOTED';
const COMMENT = /(^|[\s;&|()])#[^\n]*/g;
const STATEMENT_SEPARATOR = /(&&|\|\||;|\n)/;
const CHAINING_SEPARATORS = [';', '&&', '\n'];
const ASSIGNMENT_PREFIX = /^[A-Za-z_]\w*=/;
const GRADLE_COMMAND = /^gradle$|(?:^|\/)gradlew$/;
const SHORT_OPTIONS_ENDING_IN_O = /^-[a-zA-Z]*o$/;
const INSTALL_TASK = /(^|:)install[A-Z]/;
const OPTIONS_TAKING_A_NON_INSTALL_VALUE = ['-x', '--exclude-task', '--task', '--tests'];
const NON_EMPTY_SERIAL = /^ANDROID_SERIAL=./;

function removeHeredocBodies(command) {
  const keptLines = [];
  const openHeredocs = [];
  let closedHeredocs = 0;
  for (const line of command.split('\n')) {
    if (closedHeredocs < openHeredocs.length) {
      const { stripsTabs, delimiter } = openHeredocs[closedHeredocs];
      if ((stripsTabs ? line.replace(/^\t+/, '') : line) === delimiter) closedHeredocs++;
      continue;
    }
    keptLines.push(line);
    for (const match of line.matchAll(HEREDOC_START)) {
      openHeredocs.push({ stripsTabs: match[1] === '-', delimiter: match[2] ?? match[3] ?? match[4] });
    }
  }
  return keptLines.join('\n');
}

// Each statement keeps the separator that follows it; blank statements are dropped.
function splitStatements(command) {
  const text = removeHeredocBodies(command)
    .replace(QUOTED_TEXT, QUOTED_MARKER)
    .replace(COMMENT, '$1')
    .replace(/\\\n/g, '')
    .replace(/(\|&?)\s*\n/g, '$1 ');
  const parts = text.split(STATEMENT_SEPARATOR);
  const statements = [];
  for (let i = 0; i < parts.length; i += 2) {
    const statementText = parts[i].trim();
    if (statementText) statements.push({ text: statementText, separator: parts[i + 1] });
  }
  return statements;
}

function firstStage(statementText) {
  const words = statementText.split('|')[0].trim().split(/\s+/);
  const commandIndex = words.findIndex((word) => !ASSIGNMENT_PREFIX.test(word));
  if (commandIndex < 0) return { prefixes: words, command: '', args: [] };
  return { prefixes: words.slice(0, commandIndex), command: words[commandIndex], args: words.slice(commandIndex + 1) };
}

function setsPipefail(statementText) {
  const words = statementText.split(/\s+/);
  return words[0] === 'set' && words.some((word, i) => SHORT_OPTIONS_ENDING_IN_O.test(word) && words[i + 1] === 'pipefail');
}

function losesGradleExitCode(statements) {
  let pipefailSet = false;
  for (let i = 0; i < statements.length; i++) {
    const { text, separator } = statements[i];
    const hasFollowingStatement = i < statements.length - 1;
    if (
      !pipefailSet &&
      hasFollowingStatement &&
      CHAINING_SEPARATORS.includes(separator) &&
      text.includes('|') &&
      GRADLE_COMMAND.test(firstStage(text).command)
    ) {
      return true;
    }
    if (setsPipefail(text)) pipefailSet = true;
  }
  return false;
}

function namesInstallTask(args) {
  return args.some(
    (word, i) => !word.startsWith('-') && INSTALL_TASK.test(word) && !OPTIONS_TAKING_A_NON_INSTALL_VALUE.includes(args[i - 1]),
  );
}

function installsOnEveryDevice(statements) {
  let serialExported = false;
  for (const { text } of statements) {
    const { prefixes, command, args } = firstStage(text);
    if (
      GRADLE_COMMAND.test(command) &&
      namesInstallTask(args) &&
      !serialExported &&
      !prefixes.some((word) => NON_EMPTY_SERIAL.test(word))
    ) {
      return true;
    }
    if (command === 'export' && args.some((word) => NON_EMPTY_SERIAL.test(word))) serialExported = true;
  }
  return false;
}

function stopsGradleDaemon(statements) {
  for (const { text } of statements) {
    const { command, args } = firstStage(text);
    if (GRADLE_COMMAND.test(command) && args.includes('--stop')) return true;
  }
  return false;
}

function blockReason(command) {
  const statements = splitStatements(command);
  if (losesGradleExitCode(statements)) return LOST_EXIT_CODE_MESSAGE;
  if (installsOnEveryDevice(statements)) return UNSCOPED_INSTALL_MESSAGE;
  if (stopsGradleDaemon(statements)) return STOP_DAEMON_MESSAGE;
  return null;
}

try {
  const payload = JSON.parse(fs.readFileSync(0, 'utf-8'));
  const reason = blockReason(payload.tool_input.command);
  if (reason) {
    console.error(reason);
    process.exit(2);
  }
} catch (err) {
  // Failing closed would stop every Bash call.
}
process.exit(0);

},
(require, process, console) => {
const fs = require('fs');
const os = require('os');

let payload = {};
try {
  payload = JSON.parse(fs.readFileSync(0, 'utf-8'));
} catch (err) {
  // unparsable payload: no session id, so the hook allows
}

const cwd = payload.cwd || process.cwd();
const cmd = (payload.tool_input || {}).command || '';
const WORKTREE_HINT = 'Work in a worktree: call the EnterWorktree tool (name <issue>-<slug>), then git branch -m <branch>. If that tool is not available, run: fediim worktree <slug> --branch <branch> and run every later command from the path it prints.';
const deny = (message) => { console.error(message); process.exit(2); };

const { execFileSync } = require('child_process');
const path = require('path');

function shellWords(s) {
  return (s.match(/(?:'[^']*'|"[^"]*"|[^\s'"])+/g) || []).map((w) => w.replace(/['"\\]/g, ''));
}

const gitCache = new Map();
function gitOut(dir, args) {
  const key = dir + '\0' + args.join('\0');
  if (gitCache.has(key)) return gitCache.get(key);
  try {
    const out = execFileSync('git', ['-C', dir, ...args], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
    gitCache.set(key, out);
    return out;
  } catch (err) {
    gitCache.set(key, '');
    return '';
  }
}

// ponytail: git checkout <existing-branch> is not detected; a later gh pr create from it is.
// ponytail: M94 gaps: plumbing commits (commit-tree, update-ref) and git switch <existing-branch> in the main checkout are not detected; fediim doctor reports a main checkout off the default branch.
function taskEvents(command, startDir) {
  const events = [];
  let dir = startDir;
  const masked = command.replace(/'[^']*'|"[^"]*"/g, (q) => q.replace(/[;&|\n()]/g, ' '));
  for (const segment of masked.split(/&&|\|\||[;|&\n()]/)) {
    const w = shellWords(segment);
    if (w[0] === 'cd') { dir = path.resolve(dir, w[1] || '.'); continue; }
    const start = w.findIndex((x) => !/^\w+=/.test(x));
    const g = start >= 0 && (w[start] === 'git' || w[start].endsWith('/git')) ? start : -1;
    if (g >= 0) {
      let d = dir;
      let i = g + 1;
      for (; i < w.length && w[i].startsWith('-'); i++) {
        if (w[i] === '-C') d = path.resolve(d, w[++i] || '.');
        else if (/^(-c|--git-dir|--work-tree|--namespace|--exec-path)$/.test(w[i])) i++;
      }
      const args = w.slice(i + 1);
      const flagValue = (re) => { const k = args.findIndex((a) => re.test(a)); return k >= 0 ? args[k + 1] : undefined; };
      if (w[i] === 'checkout') {
        const n = flagValue(/^-[bB]$/);
        if (n) events.push({ name: n, dir: d, kind: 'branch' });
      } else if (w[i] === 'switch') {
        const n = flagValue(/^(-c|-C|--create|--force-create)$/);
        if (n) events.push({ name: n, dir: d, kind: 'branch' });
      } else if (w[i] === 'commit') {
        events.push({ dir: d, kind: 'commit' });
      } else if (w[i] === 'worktree' && args[0] === 'add') {
        // Only a new branch is a new task: -b/-B, or a bare <path> (git names the
        // branch after it). --detach or <path> <commit-ish> (verifying main,
        // updating an existing PR branch for a merge) stays in this session.
        const rest = args.slice(1);
        const k = rest.findIndex((a) => /^-[bB]$/.test(a));
        let n = k >= 0 ? rest[k + 1] : undefined;
        if (!n && !rest.some((a) => a === '-d' || a === '--detach')) {
          const positional = [];
          for (let j = 0; j < rest.length; j++) {
            if (rest[j] === '--reason') j++;
            else if (!rest[j].startsWith('-')) positional.push(rest[j]);
          }
          if (positional.length === 1) n = path.basename(positional[0]);
        }
        const pos = [];
        for (let j = 0; j < rest.length; j++) {
          if (/^(-b|-B|--reason)$/.test(rest[j])) j++;
          else if (!rest[j].startsWith('-')) pos.push(rest[j]);
        }
        events.push({ name: n, dir: d, kind: 'worktree', target: pos[0] ? path.resolve(d, pos[0]) : '' });
      }
    }
    if (start >= 0 && w[start] === 'gh' && w[start + 1] === 'pr' && w[start + 2] === 'create') {
      const k = w.findIndex((a, j) => j > start + 2 && (a === '--head' || a === '-H' || a.startsWith('--head=')));
      const head = k < 0 ? '' : w[k].startsWith('--head=') ? w[k].slice(7) : w[k + 1] || '';
      events.push({ name: head.replace(/^[^:]*:/, '') || gitOut(dir, ['symbolic-ref', '--short', '-q', 'HEAD']), dir });
    }
  }
  return events;
}

function realOrNearest(p) {
  let cur = path.resolve(p);
  let rest = '';
  while (!fs.existsSync(cur) && path.dirname(cur) !== cur) { rest = path.join(path.basename(cur), rest); cur = path.dirname(cur); }
  return path.join(fs.realpathSync(cur), rest);
}

function gitPath(dir, arg) {
  const o = gitOut(dir, ['rev-parse', arg]);
  return o ? fs.realpathSync(path.resolve(dir, o)) : '';
}

function isMainCheckout(dir) {
  try {
    const a = gitPath(dir, '--git-dir');
    return a !== '' && a === gitPath(dir, '--git-common-dir');
  } catch (err) {
    return false;
  }
}

// M94: new work happens in a worktree under .claude/worktrees/. Fails open on any git error.
function worktreeDenial() {
  try {
    for (const ev of taskEvents(cmd, cwd)) {
      if (ev.kind === 'branch' && isMainCheckout(ev.dir)) return 'New work never starts in the main checkout (' + ev.dir + '). ' + WORKTREE_HINT;
      if (ev.kind === 'commit' && isMainCheckout(ev.dir)) {
        const def = gitOut(ev.dir, ['symbolic-ref', '--short', 'refs/remotes/origin/HEAD']).replace(/^origin\//, '');
        if (def && def === gitOut(ev.dir, ['symbolic-ref', '--short', '-q', 'HEAD'])) return 'The main checkout stays on ' + def + ' and never gets commits. ' + WORKTREE_HINT;
      }
      if (ev.kind === 'worktree' && ev.target) {
        const common = gitPath(ev.dir, '--git-common-dir');
        if (!common) continue;
        const inside = [path.join(path.dirname(common), '.claude', 'worktrees'), path.join(os.homedir(), '.gemini', 'antigravity', 'worktrees')]
          .some((r) => (realOrNearest(ev.target) + path.sep).startsWith(realOrNearest(r) + path.sep));
        if (!inside) return 'Worktrees live only in .claude/worktrees/ (or Antigravity\'s own folder). ' + WORKTREE_HINT;
      }
    }
  } catch (err) {
    // fail open
  }
  return '';
}

const worktreeMessage = worktreeDenial();
if (worktreeMessage) deny(worktreeMessage);

// M95: stamp the linked worktree a command runs in; fediim session prune keeps one stamped in the last hour.
// No git call: in a linked worktree .git is a file naming its admin folder. Fails open.
function markUsed(dir) {
  try {
    let cur = path.resolve(dir);
    while (!fs.existsSync(path.join(cur, '.git')) && path.dirname(cur) !== cur) cur = path.dirname(cur);
    const dotGit = path.join(cur, '.git');
    if (!fs.statSync(dotGit).isFile()) return;
    const m = /^gitdir: (.+)$/m.exec(fs.readFileSync(dotGit, 'utf8'));
    if (m) fs.writeFileSync(path.join(path.resolve(cur, m[1].trim()), 'fediim-used'), '');
  } catch (err) {
    // not a worktree, or not writable
  }
}
markUsed(cwd);
for (const m of cmd.matchAll(/(?:^|&&|;|\n)\s*cd\s+("[^"]+"|'[^']+'|[^\s;&|]+)/g)) markUsed(path.resolve(cwd, m[1].replace(/^["']|["']$/g, '')));
process.exit(0);

},
];
for (const body of CHECKS) {
  const r = runCheck(body);
  if (r.out) process.stdout.write(r.out);
}
process.exit(0);
