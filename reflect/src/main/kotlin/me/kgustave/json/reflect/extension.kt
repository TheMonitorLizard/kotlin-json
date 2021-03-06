/*
 * Copyright 2018 Kaidan Gustave
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.kgustave.json.reflect

import me.kgustave.json.JSObject

// JSDeserializer

@JSDeserialization
inline fun <reified T: Any> JSDeserializer.deserialize(json: JSObject): T = deserialize(json, T::class)
@JSDeserialization
inline fun <reified T: Any> JSDeserializer.register() = register(T::class)

// JSSerializer

@JSSerialization
inline fun <reified T: Any> JSSerializer.register() = register(T::class)
