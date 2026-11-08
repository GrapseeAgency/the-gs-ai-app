import React from 'react';
import { createRoot } from 'react-dom/client';
import { flushSync } from 'react-dom';
import { transform } from '@babel/standalone';
window.GSReact = { React, createRoot, flushSync, transform };
