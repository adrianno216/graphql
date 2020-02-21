/*
 * Copyright 2020 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.smallrye.graphql.schema;

/**
 * Argument class is not found
 * 
 * @author Phillip Kruger (phillip.kruger@redhat.com)
 */
public class ArgumentTypeNotFoundException extends RuntimeException {

    public ArgumentTypeNotFoundException() {
    }

    public ArgumentTypeNotFoundException(String string) {
        super(string);
    }

    public ArgumentTypeNotFoundException(String string, Throwable thrwbl) {
        super(string, thrwbl);
    }

    public ArgumentTypeNotFoundException(Throwable thrwbl) {
        super(thrwbl);
    }

    public ArgumentTypeNotFoundException(String string, Throwable thrwbl, boolean bln, boolean bln1) {
        super(string, thrwbl, bln, bln1);
    }

}
