/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2008, Red Hat Middleware LLC or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Middleware LLC.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.cfg.annotations;

import javax.persistence.EmbeddedId;
import javax.persistence.Id;

import org.hibernate.AnnotationException;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.GenerationTime;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.OptimisticLock;
import org.hibernate.annotations.common.reflection.XClass;
import org.hibernate.annotations.common.reflection.XProperty;
import org.hibernate.annotations.common.AssertionFailure;
import org.hibernate.cfg.AccessType;
import org.hibernate.cfg.Ejb3Column;
import org.hibernate.cfg.ExtendedMappings;
import org.hibernate.cfg.PropertyHolder;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.PropertyGeneration;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.Value;
import org.hibernate.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Emmanuel Bernard
 */
public class PropertyBinder {
	private Logger log = LoggerFactory.getLogger( PropertyBinder.class );
	private String name;
	private String returnedClassName;
	private boolean lazy;
	private AccessType accessType;
	private Ejb3Column[] columns;
	private PropertyHolder holder;
	private ExtendedMappings mappings;
	private Value value;
	private boolean insertable = true;
	private boolean updatable = true;
	private String cascade;
	private SimpleValueBinder simpleValueBinder;
	private XClass declaringClass;
	private boolean declaringClassSet;
	
	/*
	 * property can be null
	 * prefer propertyName to property.getName() since some are overloaded
	 */
	private XProperty property;
	private XClass returnedClass;

	public void setInsertable(boolean insertable) {
		this.insertable = insertable;
	}

	public void setUpdatable(boolean updatable) {
		this.updatable = updatable;
	}


	public void setName(String name) {
		this.name = name;
	}

	public void setReturnedClassName(String returnedClassName) {
		this.returnedClassName = returnedClassName;
	}

	public void setLazy(boolean lazy) {
		this.lazy = lazy;
	}

	public void setAccessType(AccessType accessType) {
		this.accessType = accessType;
	}

	public void setColumns(Ejb3Column[] columns) {
		insertable = columns[0].isInsertable();
		updatable = columns[0].isUpdatable();
		//consistency is checked later when we know the property name
		this.columns = columns;
	}

	public void setHolder(PropertyHolder holder) {
		this.holder = holder;
	}

	public void setValue(Value value) {
		this.value = value;
	}

	public void setCascade(String cascadeStrategy) {
		this.cascade = cascadeStrategy;
	}

	public void setMappings(ExtendedMappings mappings) {
		this.mappings = mappings;
	}

	public void setDeclaringClass(XClass declaringClass) {
		this.declaringClass = declaringClass;
		this.declaringClassSet = true;
	}

	private void validateBind() {
		if (property.isAnnotationPresent(Immutable.class)) {
			throw new AnnotationException("@Immutable on property not allowed. " +
					"Only allowed on entity level or on a collection.");
		}
		if ( !declaringClassSet ) {
			throw new AssertionFailure( "declaringClass has not been set before a bind");
		}
	}

	private void validateMake() {
		//TODO check necessary params for a make
	}

	public Property bind() {
		validateBind();
		log.debug( "binding property {} with lazy={}", name, lazy );
		String containerClassName = holder == null ?
				null :
				holder.getClassName();
		simpleValueBinder = new SimpleValueBinder();
		simpleValueBinder.setMappings( mappings );
		simpleValueBinder.setPropertyName( name );
		simpleValueBinder.setReturnedClassName( returnedClassName );
		simpleValueBinder.setColumns( columns );
		simpleValueBinder.setPersistentClassName( containerClassName );
		simpleValueBinder.setType( property, returnedClass );
		simpleValueBinder.setMappings( mappings );
		SimpleValue propertyValue = simpleValueBinder.make();
		setValue( propertyValue );
		Property prop = make();
		holder.addProperty( prop, columns, declaringClass );
		return prop;
	}

	public Property make() {
		validateMake();
		log.debug( "Building property " + name );
		Property prop = new Property();
		prop.setName( name );
		prop.setNodeName( name );
		prop.setValue( value );
		prop.setLazy( lazy );
		prop.setCascade( cascade );
		prop.setPropertyAccessorName( accessType.getType() );
		Generated ann = property != null ?
				property.getAnnotation( Generated.class ) :
				null;
		GenerationTime generated = ann != null ?
				ann.value() :
				null;
		if ( generated != null ) {
			if ( !GenerationTime.NEVER.equals( generated ) ) {
				if ( property.isAnnotationPresent( javax.persistence.Version.class )
						&& GenerationTime.INSERT.equals( generated ) ) {
					throw new AnnotationException( "@Generated(INSERT) on a @Version property not allowed, use ALWAYS: "
							+ StringHelper.qualify( holder.getPath(), name ) );
				}
				insertable = false;
				if ( GenerationTime.ALWAYS.equals( generated ) ) {
					updatable = false;
				}
				prop.setGeneration( PropertyGeneration.parse( generated.toString().toLowerCase() ) );
			}
		}
		NaturalId naturalId = property != null ?
				property.getAnnotation( NaturalId.class ) :
				null;
		if ( naturalId != null ) {
			if ( !naturalId.mutable() ) {
				updatable = false;
			}
			prop.setNaturalIdentifier( true );
		}
		prop.setInsertable( insertable );
		prop.setUpdateable( updatable );
		OptimisticLock lockAnn = property != null ?
				property.getAnnotation( OptimisticLock.class ) :
				null;
		if ( lockAnn != null ) {
			prop.setOptimisticLocked( !lockAnn.excluded() );
			//TODO this should go to the core as a mapping validation checking
			if ( lockAnn.excluded() && (
					property.isAnnotationPresent( javax.persistence.Version.class )
							|| property.isAnnotationPresent( Id.class )
							|| property.isAnnotationPresent( EmbeddedId.class ) ) ) {
				throw new AnnotationException( "@OptimisticLock.exclude=true incompatible with @Id, @EmbeddedId and @Version: "
						+ StringHelper.qualify( holder.getPath(), name ) );
			}
		}
		log.trace( "Cascading " + name + " with " + cascade );
		return prop;
	}

	public void setProperty(XProperty property) {
		this.property = property;
	}

	public void setReturnedClass(XClass returnedClass) {
		this.returnedClass = returnedClass;
	}

	public SimpleValueBinder getSimpleValueBinder() {
		return simpleValueBinder;
	}

}
