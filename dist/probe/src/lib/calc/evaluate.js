"use strict";
/**
 * The calc expression engine — the web twin of the Android evaluator.
 *
 * A recursive-descent parser over a strict whitelist: numbers, + - * / % ^,
 * parentheses, pi/e, and sqrt abs ln log10 exp sin cos tan floor ceil round
 * min max pow clamp. Everything else — identifiers, assignments, property
 * access — is a parse error, so a model cannot smuggle an expression through.
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.evaluateExpr = evaluateExpr;
exports.formatOutput = formatOutput;
const FUNCS = {
    sqrt: Math.sqrt,
    abs: Math.abs,
    ln: Math.log,
    log10: Math.log10,
    exp: Math.exp,
    sin: Math.sin,
    cos: Math.cos,
    tan: Math.tan,
    floor: Math.floor,
    ceil: Math.ceil,
    round: Math.round,
    min: Math.min,
    max: Math.max,
    pow: Math.pow,
    clamp: (v, lo, hi) => Math.min(hi, Math.max(lo, v)),
};
const CONSTS = { pi: Math.PI, e: Math.E };
class Parser {
    constructor(src, vars) {
        this.src = src;
        this.vars = vars;
        this.pos = 0;
    }
    parse() {
        const v = this.expr();
        this.ws();
        if (this.pos < this.src.length)
            throw new Error(`unexpected ${this.src[this.pos]}`);
        return v;
    }
    ws() {
        while (this.pos < this.src.length && /\s/.test(this.src[this.pos]))
            this.pos++;
    }
    peek() {
        this.ws();
        return this.src[this.pos] ?? '';
    }
    eat(token) {
        this.ws();
        if (this.src.startsWith(token, this.pos)) {
            this.pos += token.length;
            return true;
        }
        return false;
    }
    expect(token) {
        if (!this.eat(token))
            throw new Error(`expected ${token}`);
    }
    expr() {
        let v = this.term();
        for (;;) {
            if (this.eat('+'))
                v += this.term();
            else if (this.eat('-'))
                v -= this.term();
            else
                return v;
        }
    }
    term() {
        let v = this.power();
        for (;;) {
            if (this.eat('*'))
                v *= this.power();
            else if (this.eat('/'))
                v /= this.power();
            else if (this.eat('%'))
                v %= this.power();
            else
                return v;
        }
    }
    power() {
        const base = this.unary();
        if (this.eat('^'))
            return Math.pow(base, this.power());
        return base;
    }
    unary() {
        if (this.eat('-'))
            return -this.unary();
        if (this.eat('+'))
            return this.unary();
        return this.primary();
    }
    primary() {
        this.ws();
        const c = this.src[this.pos];
        if (c === '(') {
            this.pos++;
            const v = this.expr();
            this.expect(')');
            return v;
        }
        if (c === undefined || !/[a-z0-9.]/i.test(c))
            throw new Error(`unexpected ${c ?? 'end'}`);
        const num = this.number();
        if (num !== null)
            return num;
        const ident = this.ident();
        if (this.peek() === '(') {
            const fn = FUNCS[ident];
            if (!fn)
                throw new Error(`unknown function ${ident}`);
            this.pos++;
            const args = [];
            if (this.peek() !== ')') {
                args.push(this.expr());
                while (this.eat(','))
                    args.push(this.expr());
            }
            this.expect(')');
            return fn(...args);
        }
        if (ident in this.vars)
            return this.vars[ident];
        if (ident in CONSTS)
            return CONSTS[ident];
        throw new Error(`unknown identifier ${ident}`);
    }
    number() {
        this.ws();
        const m = /^\d+(\.\d+)?([eE][+-]?\d+)?/.exec(this.src.slice(this.pos));
        if (!m)
            return null;
        this.pos += m[0].length;
        return Number(m[0]);
    }
    ident() {
        this.ws();
        const m = /^[a-z][a-z0-9_]*/i.exec(this.src.slice(this.pos));
        if (!m)
            throw new Error('expected identifier');
        this.pos += m[0].length;
        return m[0];
    }
}
/** Evaluate a calc output expression. Throws on anything outside the whitelist. */
function evaluateExpr(expr, vars) {
    return new Parser(expr, vars).parse();
}
/** Format a calc output: money | percent | plain. */
function formatOutput(value, format, unit) {
    const safe = Number.isFinite(value) ? value : 0;
    if (format === 'money') {
        const symbol = unit && /^[^\d\s]/.test(unit) ? unit : '$';
        return `${symbol}${safe.toLocaleString(undefined, { maximumFractionDigits: 2 })}`;
    }
    if (format === 'percent')
        return `${(safe * 100).toLocaleString(undefined, { maximumFractionDigits: 2 })}%`;
    const num = safe.toLocaleString(undefined, { maximumFractionDigits: 4 });
    return unit ? `${num} ${unit}` : num;
}
