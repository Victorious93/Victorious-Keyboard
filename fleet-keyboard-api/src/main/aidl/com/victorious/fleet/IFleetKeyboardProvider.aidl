/*
 * Copyright (C) 2026 Victorious Keyboard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * Shared binder for every Droidcommand-AI fleet app. Returned strings are
 * inserted by the keyboard. The keyboard does not execute them.
 */
package com.victorious.fleet;

interface IFleetKeyboardProvider {
    boolean ping();
    String getProviderName();
    String getNamespace();
    byte[] getIcon();
    String getSlashCommands();
    String getSnippets();
    String getActions();
    String ask(String action, String text);
    String runCommand(String name, in String[] args);
}
