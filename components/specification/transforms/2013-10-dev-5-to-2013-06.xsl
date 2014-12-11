<?xml version = "1.0" encoding = "UTF-8"?>
<!--
#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
#
# Copyright (C) 2009 - 2014 Open Microscopy Environment
#       Massachusetts Institute of Technology,
#       National Institutes of Health,
#       University of Dundee,
#       University of Wisconsin at Madison
#
#    This library is free software; you can redistribute it and/or
#    modify it under the terms of the GNU Lesser General Public
#    License as published by the Free Software Foundation; either
#    version 2.1 of the License, or (at your option) any later version.
#
#    This library is distributed in the hope that it will be useful,
#    but WITHOUT ANY WARRANTY; without even the implied warranty of
#    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
#    Lesser General Public License for more details.
#
#    You should have received a copy of the GNU Lesser General Public
#    License along with this library; if not, write to the Free Software
#    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
#
#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
-->

<!--
#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
# Written by:  Andrew Patterson: ajpatterson at lifesci.dundee.ac.uk
#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
-->

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:OME="http://www.openmicroscopy.org/Schemas/OME/2013-10-dev-5"
	xmlns:Bin="http://www.openmicroscopy.org/Schemas/BinaryFile/2013-10-dev-5"
	xmlns:SPW="http://www.openmicroscopy.org/Schemas/SPW/2013-10-dev-5"
	xmlns:SA="http://www.openmicroscopy.org/Schemas/SA/2013-10-dev-5"
	xmlns:ROI="http://www.openmicroscopy.org/Schemas/ROI/2013-10-dev-5"
	xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xmlns:xml="http://www.w3.org/XML/1998/namespace"
	exclude-result-prefixes="OME Bin SPW SA ROI"
	xmlns:exsl="http://exslt.org/common"
	extension-element-prefixes="exsl" version="1.0">

	<xsl:import href="units-conversion.xsl"/>

	<xsl:variable name="newOMENS">http://www.openmicroscopy.org/Schemas/OME/2013-06</xsl:variable>
	<xsl:variable name="newSPWNS">http://www.openmicroscopy.org/Schemas/SPW/2013-06</xsl:variable>
	<xsl:variable name="newBINNS"
		>http://www.openmicroscopy.org/Schemas/BinaryFile/2013-06</xsl:variable>
	<xsl:variable name="newROINS">http://www.openmicroscopy.org/Schemas/ROI/2013-06</xsl:variable>
	<xsl:variable name="newSANS">http://www.openmicroscopy.org/Schemas/SA/2013-06</xsl:variable>

	<xsl:output method="xml" indent="yes"/>
	<xsl:preserve-space elements="*"/>

	<!-- Actual schema changes -->
	
	<!-- strip EmissionWavelength and ExcitationWavelength ONLY if it is not an integer -->
	<xsl:template match="OME:Channel">
		<xsl:element name="OME:Channel" namespace="{$newOMENS}">
			<xsl:for-each select="@* [not(name() = 'EmissionWavelength' or name() = 'ExcitationWavelength')]">
				<xsl:attribute name="{local-name(.)}">
					<xsl:value-of select="."/>
				</xsl:attribute>
			</xsl:for-each>
			<xsl:variable name="theValueEm" select="@EmissionWavelength"/>
			<xsl:for-each select="@* [name() = 'EmissionWavelength']">
				<xsl:if test="$theValueEm=round($theValueEm)">
					<xsl:attribute name="{local-name(.)}">
						<xsl:value-of select="round($theValueEm)"/>
					</xsl:attribute>
				</xsl:if>
			</xsl:for-each>
			<xsl:variable name="theValueEx" select="@ExcitationWavelength"/>
			<xsl:for-each select="@* [name() = 'ExcitationWavelength']">
				<xsl:if test="$theValueEx=round($theValueEx)">
					<xsl:attribute name="{local-name(.)}">
						<xsl:value-of select="round($theValueEx)"/>
					</xsl:attribute>
				</xsl:if>
			</xsl:for-each>
			<xsl:apply-templates select="node()"/>
		</xsl:element>
	</xsl:template>

	<!-- strip Wavelength from LightSourceSettings ONLY if it is not an integer -->
	<xsl:template match="OME:LightSourceSettings">
		<xsl:element name="OME:LightSourceSettings" namespace="{$newOMENS}">
			<xsl:for-each select="@* [not(name() = 'Wavelength' or name() = 'WavelengthUnit')]">
				<xsl:attribute name="{local-name(.)}">
					<xsl:value-of select="."/>
				</xsl:attribute>
			</xsl:for-each>
			<xsl:variable name="theValue" select="@Wavelength"/>
			<xsl:for-each select="@* [name() = 'Wavelength']">
				<xsl:if test="$theValue=round($theValue)">
					<xsl:attribute name="{local-name(.)}">
						<xsl:value-of select="round($theValue)"/>
					</xsl:attribute>
				</xsl:if>
			</xsl:for-each>
			<xsl:apply-templates select="node()"/>
		</xsl:element>
	</xsl:template>

	<!-- strip Wavelength from Laser ONLY if it is not an integer -->
	<xsl:template match="OME:Laser">
		<xsl:element name="OME:Laser" namespace="{$newOMENS}">
			<xsl:for-each select="@* [not(name() = 'Wavelength' or name() = 'WavelengthUnit')]">
				<xsl:attribute name="{local-name(.)}">
					<xsl:value-of select="."/>
				</xsl:attribute>
			</xsl:for-each>
			<xsl:variable name="theValue">
				<xsl:call-template name="ConvertValueToDefault">
					<xsl:with-param name="theValue"><xsl:value-of select="@Wavelength"/></xsl:with-param>
					<xsl:with-param name="theCurrentUnit"><xsl:value-of select="@WavelengthUnit"/></xsl:with-param>
					<xsl:with-param name="theAttributeName">Wavelength</xsl:with-param>
					<xsl:with-param name="theElementName">Laser</xsl:with-param>
				</xsl:call-template>
			</xsl:variable>
			<xsl:for-each select="@* [name() = 'Wavelength']">
				<xsl:if test="$theValue=round($theValue)">
					<xsl:attribute name="{local-name(.)}">
						<xsl:value-of select="round($theValue)"/>
					</xsl:attribute>
				</xsl:if>
			</xsl:for-each>
			<xsl:apply-templates select="node()"/>
		</xsl:element>
	</xsl:template>

	<!-- strip AnnotationRef on Shape -->
	<xsl:template match="ROI:Shape">
		<xsl:element name="{name()}" namespace="{$newROINS}">
			<xsl:apply-templates select="@*"/>
			<xsl:for-each select="* [not(local-name() = 'AnnotationRef')]">
				<xsl:apply-templates select="."/>
			</xsl:for-each>
		</xsl:element>
	</xsl:template>

	<!-- strip AnnotationRef on Instrument -->
	<xsl:template match="OME:Instrument">
		<xsl:element name="{name()}" namespace="{$newOMENS}">
			<xsl:apply-templates select="@*"/>
			<xsl:for-each select="* [not(local-name() = 'AnnotationRef')]">
				<xsl:apply-templates select="."/>
			</xsl:for-each>
		</xsl:element>
	</xsl:template>

	<!-- strip AnnotationRef on Objective -->
	<xsl:template match="OME:Objective">
		<xsl:element name="{name()}" namespace="{$newOMENS}">
			<xsl:apply-templates select="@*"/>
			<xsl:for-each select="* [not(local-name() = 'AnnotationRef')]">
				<xsl:apply-templates select="."/>
			</xsl:for-each>
		</xsl:element>
	</xsl:template>

	<!-- strip AnnotationRef on Detector -->
	<xsl:template match="OME:Detector">
		<xsl:element name="{name()}" namespace="{$newOMENS}">
			<xsl:apply-templates select="@*"/>
			<xsl:for-each select="* [not(local-name() = 'AnnotationRef')]">
				<xsl:apply-templates select="."/>
			</xsl:for-each>
		</xsl:element>
	</xsl:template>

	<!-- strip AnnotationRef on Filter -->
	<xsl:template match="OME:Filter">
		<xsl:element name="{name()}" namespace="{$newOMENS}">
			<xsl:apply-templates select="@*"/>
			<xsl:for-each select="* [not(local-name() = 'AnnotationRef')]">
				<xsl:apply-templates select="."/>
			</xsl:for-each>
		</xsl:element>
	</xsl:template>

	<!-- strip AnnotationRef on Dichroic -->
	<xsl:template match="OME:Dichroic">
		<xsl:element name="{name()}" namespace="{$newOMENS}">
			<xsl:apply-templates select="@*"/>
			<xsl:for-each select="* [not(local-name() = 'AnnotationRef')]">
				<xsl:apply-templates select="."/>
			</xsl:for-each>
		</xsl:element>
	</xsl:template>

	<!-- strip AnnotationRef on LightPath -->
	<xsl:template match="OME:LightPath">
		<xsl:element name="{name()}" namespace="{$newOMENS}">
			<xsl:apply-templates select="@*"/>
			<xsl:for-each select="* [not(local-name() = 'AnnotationRef')]">
				<xsl:apply-templates select="."/>
			</xsl:for-each>
		</xsl:element>
	</xsl:template>

	<!-- strip AnnotationRef on LightSource -->
	<xsl:template match="OME:LightSource">
		<xsl:element name="{name()}" namespace="{$newOMENS}">
			<xsl:apply-templates select="@*"/>
			<xsl:for-each select="* [not(local-name() = 'AnnotationRef')]">
				<xsl:apply-templates select="."/>
			</xsl:for-each>
		</xsl:element>
	</xsl:template>

	<!-- strip GenericExcitationSource and terminate -->
	<xsl:template match="OME:GenericExcitationSource">
		<xsl:comment>GenericExcitationSource elements cannot be converted to 2013-06 Schema, they are not supported.</xsl:comment>
		<xsl:message terminate="yes">OME-XSLT: 2013-10-dev-5-to-2013-06.xsl - ERROR - GenericExcitationSource elements cannot be converted to 2011-06 Schema, they are not supported.</xsl:message>
	</xsl:template>

	<!-- strip GenericExcitationSource and terminate -->
	<xsl:template match="SA:MapAnnotation">
		<xsl:comment>MapAnnotation elements cannot be converted to 2013-06 Schema, they are not supported.</xsl:comment>
		<xsl:message terminate="yes">OME-XSLT: 2013-10-dev-5-to-2013-06.xsl - ERROR - MapAnnotation elements cannot be converted to 2011-06 Schema, they are not supported.</xsl:message>
	</xsl:template>

	<!-- Rewriting all namespaces -->

	<xsl:template match="OME:OME">
		<OME:OME xmlns:OME="http://www.openmicroscopy.org/Schemas/OME/2013-06"
			xmlns:Bin="http://www.openmicroscopy.org/Schemas/BinaryFile/2013-06"
			xmlns:SPW="http://www.openmicroscopy.org/Schemas/SPW/2013-06"
			xmlns:SA="http://www.openmicroscopy.org/Schemas/SA/2013-06"
			xmlns:ROI="http://www.openmicroscopy.org/Schemas/ROI/2013-06"
			xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
			xsi:schemaLocation="http://www.openmicroscopy.org/Schemas/OME/2013-06
			http://www.openmicroscopy.org/Schemas/OME/2013-06/ome.xsd">
			<xsl:apply-templates/>
		</OME:OME>
	</xsl:template>

	<xsl:template match="OME:*">
		<xsl:element name="{name()}" namespace="{$newOMENS}">
			<xsl:call-template name="attribute-units-conversion"/>
			<xsl:apply-templates select="node()"/>
		</xsl:element>
	</xsl:template>

	<xsl:template match="Bin:*">
		<xsl:element name="{name()}" namespace="{$newBINNS}">
			<xsl:apply-templates select="@*|node()"/>
		</xsl:element>
	</xsl:template>

	<xsl:template match="SA:*">
		<xsl:element name="{name()}" namespace="{$newSANS}">
			<xsl:apply-templates select="@*|node()"/>
		</xsl:element>
	</xsl:template>

	<xsl:template match="SPW:*">
		<xsl:element name="{name()}" namespace="{$newSPWNS}">
			<xsl:apply-templates select="@*|node()"/>
		</xsl:element>
	</xsl:template>

	<xsl:template match="ROI:*">
		<xsl:element name="{name()}" namespace="{$newROINS}">
			<xsl:apply-templates select="@*|node()"/>
		</xsl:element>
	</xsl:template>

	<xsl:template name="attribute-units-conversion">
		<xsl:variable name="unit-attributes" select="@* [substring(name(), string-length(name()) - string-length('Unit') +1) = 'Unit']"/>
		<xsl:variable name="unit-attributes-count" select="count($unit-attributes)"/>
		<xsl:for-each select="@*">
			<xsl:choose>
				<xsl:when test="substring(name(), string-length(name()) - string-length('Unit') +1) = 'Unit'">
					<xsl:message>Skipping: <xsl:value-of select="name()"/></xsl:message>
				</xsl:when>
				<xsl:when test="$unit-attributes-count != 0">
					<xsl:message>Checking: <xsl:value-of select="name()"/></xsl:message>
					<xsl:variable name="match-name"><xsl:value-of select="name()"/>Unit</xsl:variable>
					<xsl:choose>
						<xsl:when test="count($unit-attributes[name() = $match-name]) = 1">
							<xsl:message>Match: <xsl:value-of select="name()"/></xsl:message>
							<xsl:apply-templates select="."/>
						</xsl:when>
						<xsl:otherwise>
							<xsl:message>No match: <xsl:value-of select="name()"/></xsl:message>
							<xsl:apply-templates select="."/>
						</xsl:otherwise>
					</xsl:choose>
				</xsl:when>
				<xsl:otherwise>
					<xsl:apply-templates select="."/>
				</xsl:otherwise>
			</xsl:choose>
		</xsl:for-each>
	</xsl:template>
	<!-- Default processing -->
	<xsl:template match="@*|node()">
		<xsl:copy>
			<xsl:apply-templates select="@*|node()"/>
		</xsl:copy>
	</xsl:template>

</xsl:stylesheet>
