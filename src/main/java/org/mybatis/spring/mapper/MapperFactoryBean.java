/**
 * Copyright 2010-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mybatis.spring.mapper;

import static org.springframework.util.Assert.notNull;

import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.session.Configuration;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.support.SqlSessionDaoSupport;
import org.springframework.beans.factory.FactoryBean;

/**
 * BeanFactory that enables injection of MyBatis mapper interfaces. It can be set up with a SqlSessionFactory or a
 * pre-configured SqlSessionTemplate.
 * <p>
 * Sample configuration:
 *
 * <pre class="code">
 * {@code
 *   <bean id="baseMapper" class="org.mybatis.spring.mapper.MapperFactoryBean" abstract="true" lazy-init="true">
 *     <property name="sqlSessionFactory" ref="sqlSessionFactory" />
 *   </bean>
 *
 *   <bean id="oneMapper" parent="baseMapper">
 *     <property name="mapperInterface" value="my.package.MyMapperInterface" />
 *   </bean>
 *
 *   <bean id="anotherMapper" parent="baseMapper">
 *     <property name="mapperInterface" value="my.package.MyAnotherMapperInterface" />
 *   </bean>
 * }
 * </pre>
 * <p>
 * Note that this factory can only inject <em>interfaces</em>, not concrete classes.
 *
 *
 *
 * 整体流程：
 * - MapperFactoryBean 是一个 FactoryBean 也是一个 InitializingBean（由 DaoSupport 继承而来）
 *
 * 启动流程：
 * - FactoryBean.getObject()：先执行，创建对应的 Spring 对象，之后可以被 @Autowired 注入使用。
 * - InitializingBean.afterPropertiesSet()：后执行，由 DaoSupport.afterPropertiesSet 调到 MapperFactoryBean.checkDaoConfig() 主要解析生成 MapperStatement 运行时使用，会从两个地方查找并解析
 *    - XML配置：会查找此 XxxMapper Bean 对应的 XxxMapper.xml 文件，解析生成 MapperStatement
 *    - 注解配置：会查找此 XxxMapper method 的各种注解解析生成 MapperStatement 下面详细介绍下整个流程：
 *
 *
 * @author Eduardo Macarron
 *
 * @see SqlSessionTemplate
 */
public class MapperFactoryBean<T> extends SqlSessionDaoSupport implements FactoryBean<T> {

  private Class<T> mapperInterface;

  private boolean addToConfig = true;

  public MapperFactoryBean() {
    // intentionally empty
  }

  public MapperFactoryBean(Class<T> mapperInterface) {
    this.mapperInterface = mapperInterface;
  }

  /**
   * {@inheritDoc}
   *
   * InitializingBean.afterPropertiesSet()：后执行，主要解析生成 MapperStatement 运行时使用，会从两个地方查找并解析
   *  - XML配置：会查找此 XxxMapper Bean 对应的 XxxMapper.xml 文件，解析生成 MapperStatement
   *  - 注解配置：会查找此 XxxMapper method 的各种注解解析生成 MapperStatement 下面详细介绍下整个流程：
   *
   *  DaoSupport.afterPropertiesSet()，内部会调用到这个方法。
   */
  @Override
  protected void checkDaoConfig() {
    super.checkDaoConfig();

    notNull(this.mapperInterface, "Property 'mapperInterface' is required");

    Configuration configuration = getSqlSession().getConfiguration();
    if (this.addToConfig && !configuration.hasMapper(this.mapperInterface)) {
      try {
        // 这里调转到 Mybatis 项目的代码中。
        // 内部会用 MapperAnnotationBuilder 对这个 mapperInterface 进行解析，生成 MapperStatement
        configuration.addMapper(this.mapperInterface);
      } catch (Exception e) {
        logger.error("Error while adding the mapper '" + this.mapperInterface + "' to configuration.", e);
        throw new IllegalArgumentException(e);
      } finally {
        ErrorContext.instance().reset();
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public T getObject() throws Exception {
    // 先执行，通过 JDK 创建 Proxy 对象，内部持有 MapperProxy
    // 返回一个 SpringBean，之后可以被 @Autowired 注入使用。
    // 运行期间：
    //    @Autowired 对象调用方法时，会执行 MapperProxy@invoke()，之后会根据 method 查找对应的 MapperMethod 执行，内部会拿到 MappedStatement 对象（内有SQL和 resultMap）
    //    MappedStatement 拿到 Configuration，new 一个 StatementHandler，之后走 DB 中间件进行执行（dbcp、jdbc等等）
    return getSqlSession().getMapper(this.mapperInterface);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Class<T> getObjectType() {
    return this.mapperInterface;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isSingleton() {
    return true;
  }

  // ------------- mutators --------------

  /**
   * Sets the mapper interface of the MyBatis mapper
   *
   * @param mapperInterface
   *          class of the interface
   */
  public void setMapperInterface(Class<T> mapperInterface) {
    this.mapperInterface = mapperInterface;
  }

  /**
   * Return the mapper interface of the MyBatis mapper
   *
   * @return class of the interface
   */
  public Class<T> getMapperInterface() {
    return mapperInterface;
  }

  /**
   * If addToConfig is false the mapper will not be added to MyBatis. This means it must have been included in
   * mybatis-config.xml.
   * <p>
   * If it is true, the mapper will be added to MyBatis in the case it is not already registered.
   * <p>
   * By default addToConfig is true.
   *
   * @param addToConfig
   *          a flag that whether add mapper to MyBatis or not
   */
  public void setAddToConfig(boolean addToConfig) {
    this.addToConfig = addToConfig;
  }

  /**
   * Return the flag for addition into MyBatis config.
   *
   * @return true if the mapper will be added to MyBatis in the case it is not already registered.
   */
  public boolean isAddToConfig() {
    return addToConfig;
  }
}
