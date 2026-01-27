/*
 * # Copyright 2024-2025 NetCracker Technology Corporation
 * #
 * # Licensed under the Apache License, Version 2.0 (the "License");
 * # you may not use this file except in compliance with the License.
 * # You may obtain a copy of the License at
 * #
 * #      http://www.apache.org/licenses/LICENSE-2.0
 * #
 * # Unless required by applicable law or agreed to in writing, software
 * # distributed under the License is distributed on an "AS IS" BASIS,
 * # WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * # See the License for the specific language governing permissions and
 * # limitations under the License.
 *
 */

package org.qubership.automation.itf.activation;

import java.math.BigInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

import org.qubership.automation.itf.trigger.camel.Trigger;
import org.springframework.stereotype.Component;

@Component
public class TriggersCache {

    private ConcurrentMap<BigInteger, Trigger> triggers;

    public TriggersCache() {
        this.triggers = new ConcurrentHashMap<>();
    }

    public void put(BigInteger triggerId, Trigger trigger) {
        this.triggers.put(triggerId, trigger);
    }

    public Trigger get(BigInteger triggerId) {
        return this.triggers.get(triggerId);
    }

    public void remove(BigInteger triggerId) {
        this.triggers.remove(triggerId);
    }

    public void clear() {
        this.triggers.clear();
    }

    public void applyConsumerToEach(Consumer<Trigger> consumer) {
        this.triggers.values().forEach(consumer);
    }

    public boolean isEmpty() {
        return this.triggers.isEmpty();
    }
}
