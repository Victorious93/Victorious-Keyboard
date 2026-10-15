/*
 * Copyright (C) 2026 Victorious Keyboard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * TODO-confirm-with-hub
 * Replace this file with the hub's AIDL when it is published.
 * The keyboard adapts the stub in AidlFleetHub and does not execute returned text.
 */
package dev.patrickgold.florisboard.lib.fleet;

interface IFleetHub {
    String askHub(String action, String text);
    String runCommand(String name, in String[] args);
    String getSnippets();
    boolean ping();
}
