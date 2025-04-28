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

package org.qubership.automation.itf.ui.config;

import org.apache.cxf.binding.soap.spring.SoapVersionRegistrar;
import org.springframework.beans.factory.config.CustomEditorConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
//@ImportResource({ "classpath:META-INF/cxf/cxf.xml" })
public class CxfConfiguration {

    /** This method create CustomEditorConfigurer.
     * @return instance of {@link CustomEditorConfigurer} with {@link SoapVersionRegistrar}.
     */
    @Bean
    public CustomEditorConfigurer getCustomEditorConfigurer() {
        CustomEditorConfigurer customEditorConfigurer = new CustomEditorConfigurer();
        customEditorConfigurer.setPropertyEditorRegistrars(
                new SoapVersionRegistrar[]{
                        getSoapVersionRegistrar()
                });
        return new CustomEditorConfigurer();
    }

    @Bean
    public SoapVersionRegistrar getSoapVersionRegistrar() {
        return new SoapVersionRegistrar();
    }
}
