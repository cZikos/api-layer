/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

import React from 'react';
import { IconButton, Tooltip, Link } from '@material-ui/core';
import PowerSettingsNewIcon from '@material-ui/icons/PowerSettingsNew';
import { makeStyles, withStyles, useTheme } from '@material-ui/core/styles';

import MetricsIconButton from '../Icons/MetricsIconButton';

const useStyles = makeStyles((theme) => ({
    metricsHeaderDiv: {
        height: 65,
        backgroundColor: theme.palette.primary.main,
        display: 'flex',
    },
}));

const LogoutIconButton = withStyles((theme) => ({
    root: {
        color: theme.palette.header.main,
        padding: 10,
        margin: 10,
        marginRight: 40,
        backgroundColor: theme.palette.primary.light,
        '&:hover': {
            backgroundColor: theme.palette.primary.dark,
        },
    },
}))(IconButton);

const LogoutIcon = withStyles(() => ({
    root: {
        fontSize: 25,
    },
}))(PowerSettingsNewIcon);

const ServiceNameHeader = withStyles((theme) => ({
    root: {
        color: theme.palette.header.main,
        'align-self': 'center',
        width: '100%',
        margin: 20,
    },
}))(Link);

const Header = (props) => {
    const theme = useTheme();
    const classes = useStyles();

    const handleLogout = () => {
        const { logout } = props;
        logout();
    };

    const dashboard = '/metrics-service/ui/v1/#/dashboard';

    return (
        <div className={classes.metricsHeaderDiv}>
            <MetricsIconButton color={theme.palette.background.main} />
            <ServiceNameHeader id="name" variant="h6" align="left" underline="none" href={dashboard}>
                Metrics Service
            </ServiceNameHeader>
            <Tooltip title="Logout">
                <LogoutIconButton onClick={handleLogout} id="logout">
                    <LogoutIcon />
                </LogoutIconButton>
            </Tooltip>
        </div>
    );
};

export default Header;
