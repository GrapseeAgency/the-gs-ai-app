"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.arithmeticPreview = arithmeticPreview;
const evaluate_1 = require("../calc/evaluate");
/** Deliberately NOT a Python interpreter. Reject the whole preview on unsupported
 * syntax instead of skipping lines, inventing values, or claiming execution. */
function arithmeticPreview(source) {
    const vars = Object.create(null);
    const output = [];
    let lineNumber = 0;
    const numeric = (expr) => {
        if (!/^[a-zA-Z0-9_+*().\/\s-]+$/.test(expr) || /\*\*|\/\//.test(expr))
            throw new Error('Only numbers, variables, +, -, *, / and parentheses are supported');
        // Do not inherit the calculator's constants, case folding or functions.
        for (const name of expr.match(/[a-zA-Z_][a-zA-Z0-9_]*/g) ?? []) {
            if (!Object.prototype.hasOwnProperty.call(vars, name))
                throw new Error(`Unknown variable: ${name}`);
        }
        if (/[a-zA-Z_]\w*\s*\(/.test(expr))
            throw new Error('Function calls are not supported');
        const value = (0, evaluate_1.evaluateExpr)(expr, vars);
        if (!Number.isFinite(value))
            throw new Error('Result is not finite (check division by zero)');
        return value;
    };
    try {
        if (source.length > 32000 || source.split('\n').length > 200)
            throw new Error('Preview limit exceeded');
        for (const raw of source.split('\n')) {
            lineNumber++;
            const line = raw.trim();
            if (!line || line.startsWith('#'))
                continue;
            const assign = /^([a-zA-Z_]\w*)\s*=\s*([^=]+)$/.exec(line);
            if (assign) {
                vars[assign[1]] = numeric(assign[2]);
                continue;
            }
            const print = /^print\((.*)\)$/.exec(line);
            if (print) {
                const arg = print[1].trim();
                if (!arg) {
                    output.push('');
                    continue;
                }
                // A single unescaped string literal or a numeric expression, not eval.
                if (/^"[^"\\]*"$|^'[^'\\]*'$/.test(arg))
                    output.push(arg.slice(1, -1));
                else
                    output.push(String(numeric(arg)));
                continue;
            }
            throw new Error('Unsupported statement; imports, loops, libraries and scripts require a Python runtime');
        }
        return { output: 'Arithmetic preview (not a Python runtime)\n' + (output.length ? output.join('\n') : '(no print output)') };
    }
    catch (error) {
        return { output: '', error: `Preview stopped at line ${lineNumber}: ${error instanceof Error ? error.message : String(error)}` };
    }
}
