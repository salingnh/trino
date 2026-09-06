/*
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
package io.trino.plugin.elasticsearch.client;

import java.io.InputStream;
import java.util.function.Consumer;

/**
 * Decodes a remote search page without depending on its pagination strategy.
 */
public interface SearchResponseDecoder
{
    default SearchResult decode(InputStream input)
    {
        return decode(input, _ -> {}, _ -> {});
    }

    SearchResult decode(InputStream input, Consumer<String> scrollContext, Consumer<String> pointInTimeContext);
}
